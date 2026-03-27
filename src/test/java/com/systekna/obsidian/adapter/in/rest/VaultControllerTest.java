package com.systekna.obsidian.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systekna.obsidian.domain.model.Note;
import com.systekna.obsidian.domain.model.NoteType;
import com.systekna.obsidian.domain.model.SearchResult;
import com.systekna.obsidian.domain.port.in.ChatUseCase;
import com.systekna.obsidian.domain.port.in.SearchUseCase;
import com.systekna.obsidian.domain.port.in.TemplateUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VaultController.class)
@DisplayName("VaultController — testes de integração REST")
class VaultControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean TemplateUseCase templateUseCase;
    @MockBean ChatUseCase     chatUseCase;
    @MockBean SearchUseCase   searchUseCase;

    private Note makeNote(String id, boolean processed) {
        return new Note(id, "Título", NoteType.ADR, "conteúdo",
            Map.of(), processed, LocalDateTime.now());
    }

    @Test
    @DisplayName("POST /notes/{id}/process — 200 quando nota processada com sucesso")
    void processNote_returns200() throws Exception {
        when(templateUseCase.processNote("adr.md"))
            .thenReturn(makeNote("adr.md", true));

        mvc.perform(post("/api/v1/notes/adr.md/process"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processed").value(true));
    }

    @Test
    @DisplayName("POST /notes/{id}/process — 404 quando nota não encontrada")
    void processNote_returns404() throws Exception {
        when(templateUseCase.processNote(anyString()))
            .thenThrow(new NoSuchElementException("não encontrada"));

        mvc.perform(post("/api/v1/notes/inexistente.md/process"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /notes/{id}/process — 400 quando nota já processada")
    void processNote_returns400WhenAlreadyProcessed() throws Exception {
        when(templateUseCase.processNote(anyString()))
            .thenThrow(new IllegalStateException("já processada"));

        mvc.perform(post("/api/v1/notes/nota.md/process"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("POST /notes/process-pending — retorna contagem de notas processadas")
    void processPending_returnsCount() throws Exception {
        when(templateUseCase.processPending())
            .thenReturn(List.of(makeNote("n1.md", true), makeNote("n2.md", true)));

        mvc.perform(post("/api/v1/notes/process-pending"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processedCount").value(2));
    }

    @Test
    @DisplayName("POST /chat — retorna resposta do chat")
    void chat_returnsResponse() throws Exception {
        when(chatUseCase.chat(eq("qual banco usamos?"), anyList()))
            .thenReturn("Vocês escolheram PostgreSQL.");

        String body = mapper.writeValueAsString(
            Map.of("message", "qual banco usamos?", "history", List.of()));

        mvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.response").value("Vocês escolheram PostgreSQL."));
    }

    @Test
    @DisplayName("GET /search — retorna resultados com score e excerpt")
    void search_returnsResults() throws Exception {
        Note note = makeNote("adr.md", true);
        when(searchUseCase.search("arquitetura", 5))
            .thenReturn(List.of(new SearchResult(note, 0.87, "trecho...")));

        mvc.perform(get("/api/v1/search").param("q", "arquitetura"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].score").value(0.87))
            .andExpect(jsonPath("$[0].excerpt").value("trecho..."));
    }

    @Test
    @DisplayName("GET /search — 400 para query vazia")
    void search_returns400ForBlankQuery() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "  "))
            .andExpect(status().isBadRequest());
    }
}
