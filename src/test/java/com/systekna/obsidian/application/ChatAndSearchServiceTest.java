package com.systekna.obsidian.application;

import com.systekna.obsidian.domain.model.Note;
import com.systekna.obsidian.domain.model.NoteType;
import com.systekna.obsidian.domain.model.SearchResult;
import com.systekna.obsidian.domain.port.in.SearchUseCase;
import com.systekna.obsidian.domain.port.out.LlmPort;
import com.systekna.obsidian.domain.port.out.SearchPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService e SearchService")
class ChatAndSearchServiceTest {

    @Mock LlmPort      llm;
    @Mock SearchPort   searchStore;
    @Mock SearchUseCase searchUseCase;

    ChatService  chatService;
    SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(llm, searchStore);
        chatService   = new ChatService(llm, searchService);
    }

    private Note makeNote(String id, String title) {
        return new Note(id, title, NoteType.ADR, "conteúdo",
            Map.of(), false, LocalDateTime.now());
    }

    // ─── SearchService ────────────────────────────────────────────────────────

    @Test
    @DisplayName("search gera embedding da query e delega ao SearchPort")
    void search_generatesEmbeddingAndDelegates() {
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        Note note = makeNote("adr.md", "ADR 001");
        SearchResult result = new SearchResult(note, 0.85, "trecho relevante");

        when(llm.embed("arquitetura hexagonal")).thenReturn(embedding);
        when(searchStore.search(embedding, 5)).thenReturn(List.of(result));

        List<SearchResult> results = searchService.search("arquitetura hexagonal", 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).score()).isEqualTo(0.85);
        verify(llm).embed("arquitetura hexagonal");
        verify(searchStore).search(embedding, 5);
    }

    @Test
    @DisplayName("search retorna lista vazia para query em branco")
    void search_returnsEmptyForBlankQuery() {
        List<SearchResult> results = searchService.search("   ", 5);
        assertThat(results).isEmpty();
        verifyNoInteractions(llm, searchStore);
    }

    @Test
    @DisplayName("search retorna lista vazia para query nula")
    void search_returnsEmptyForNullQuery() {
        List<SearchResult> results = searchService.search(null, 5);
        assertThat(results).isEmpty();
    }

    // ─── ChatService ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("chat injeta notas relevantes no system prompt")
    void chat_injectsRelevantNotesInSystemPrompt() {
        float[] embedding = new float[]{0.1f, 0.2f};
        Note note = makeNote("adr.md", "ADR Banco de Dados");
        SearchResult relevant = new SearchResult(note, 0.9, "escolhemos PostgreSQL");

        when(llm.embed("qual banco usamos?")).thenReturn(embedding);
        when(searchStore.search(embedding, 3)).thenReturn(List.of(relevant));
        when(llm.chat(anyString(), anyList(), eq("qual banco usamos?")))
            .thenReturn("Vocês escolheram PostgreSQL conforme ADR.");

        String response = chatService.chat("qual banco usamos?", List.of());

        assertThat(response).contains("PostgreSQL");
        verify(llm).chat(
            argThat(sys -> sys.contains("ADR Banco de Dados")),
            anyList(),
            eq("qual banco usamos?")
        );
    }

    @Test
    @DisplayName("chat funciona sem notas relevantes no vault")
    void chat_worksWithNoRelevantNotes() {
        when(llm.embed(anyString())).thenReturn(new float[]{});
        when(searchStore.search(any(), anyInt())).thenReturn(List.of());
        when(llm.chat(anyString(), anyList(), anyString())).thenReturn("Não encontrei notas.");

        String response = chatService.chat("pergunta qualquer", List.of());

        assertThat(response).isNotBlank();
    }

    // ─── SearchResult ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("SearchResult.isRelevant retorna true para score >= 0.6")
    void searchResult_isRelevantAboveThreshold() {
        Note note = makeNote("n.md", "N");
        assertThat(new SearchResult(note, 0.6, "x").isRelevant()).isTrue();
        assertThat(new SearchResult(note, 0.9, "x").isRelevant()).isTrue();
        assertThat(new SearchResult(note, 0.59, "x").isRelevant()).isFalse();
    }

    @Test
    @DisplayName("SearchResult rejeita score fora de 0-1")
    void searchResult_rejectsInvalidScore() {
        Note note = makeNote("n.md", "N");
        assertThatThrownBy(() -> new SearchResult(note, 1.1, "x"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchResult(note, -0.1, "x"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
