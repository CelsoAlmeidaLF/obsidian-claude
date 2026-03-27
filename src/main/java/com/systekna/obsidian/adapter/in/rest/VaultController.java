package com.systekna.obsidian.adapter.in.rest;

import com.systekna.obsidian.domain.model.Note;
import com.systekna.obsidian.domain.model.SearchResult;
import com.systekna.obsidian.domain.port.in.ChatUseCase;
import com.systekna.obsidian.domain.port.in.SearchUseCase;
import com.systekna.obsidian.domain.port.in.TemplateUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Adapter driving: REST API consumida pelo plugin do Obsidian e pelo JavaFX.
 */
@RestController
@RequestMapping("/api/v1")
public class VaultController {

    private final TemplateUseCase templateUseCase;
    private final ChatUseCase     chatUseCase;
    private final SearchUseCase   searchUseCase;

    public VaultController(TemplateUseCase templateUseCase,
                            ChatUseCase chatUseCase,
                            SearchUseCase searchUseCase) {
        this.templateUseCase = templateUseCase;
        this.chatUseCase     = chatUseCase;
        this.searchUseCase   = searchUseCase;
    }

    /** Processa uma nota específica pelo seu id (caminho relativo ao vault) */
    @PostMapping("/notes/{noteId}/process")
    public ResponseEntity<?> processNote(@PathVariable String noteId) {
        try {
            Note result = templateUseCase.processNote(noteId);
            return ResponseEntity.ok(Map.of(
                "noteId",    result.getId(),
                "processed", result.isClaudeProcessed()
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Processa todas as notas pendentes */
    @PostMapping("/notes/process-pending")
    public ResponseEntity<?> processPending() {
        List<Note> processed = templateUseCase.processPending();
        return ResponseEntity.ok(Map.of(
            "processedCount", processed.size(),
            "notes", processed.stream().map(Note::getId).toList()
        ));
    }

    /** Chat interativo com contexto do vault */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest req) {
        String response = chatUseCase.chat(req.message(), req.history());
        return ResponseEntity.ok(Map.of("response", response));
    }

    /** Chat Streaming (SSE) com contexto do vault */
    @PostMapping(value = "/chat/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter chatStream(@RequestBody ChatRequest req) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(120000L); // 2 minutos
        
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                chatUseCase.chatStream(req.message(), req.history(), token -> {
                    try {
                        emitter.send(token);
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }

    /** Busca semântica no vault */
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int limit) {
        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query não pode ser vazia"));
        }
        List<SearchResult> results = searchUseCase.search(q, limit);
        return ResponseEntity.ok(results.stream().map(r -> Map.of(
            "noteId",  r.note().getId(),
            "title",   r.note().getTitle(),
            "score",   r.score(),
            "excerpt", r.excerpt()
        )).toList());
    }

    // ─── DTOs ────────────────────────────────────────────────────────────────

    public record ChatRequest(String message, List<String[]> history) {}
}
