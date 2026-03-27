package com.systekna.obsidian.application;

import com.systekna.obsidian.domain.model.Note;
import com.systekna.obsidian.domain.model.NoteType;
import com.systekna.obsidian.domain.port.out.LlmPort;
import com.systekna.obsidian.domain.port.out.VaultPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TemplateService — use case de processamento")
class TemplateServiceTest {

    @Mock VaultPort vault;
    @Mock LlmPort   llm;

    TemplateService service;

    private static final String CONTENT = """
        ---
        tipo: adr
        claude_processado: false
        ---
        ## Contexto
        <!-- claude:contexto -->
        Precisamos escolher entre PostgreSQL e MongoDB para o projeto.

        ## 🤖 Análise Claude
        <!-- claude:output -->
        > *Esta seção é preenchida automaticamente.*
        """;

    @BeforeEach
    void setUp() {
        service = new TemplateService(vault, llm);
    }

    private Note makeNote(String id, boolean processed) {
        return new Note(id, "ADR 001", NoteType.ADR, CONTENT,
            Map.of("tipo", "adr", "claude_processado", processed),
            processed, LocalDateTime.now());
    }

    @Test
    @DisplayName("processNote chama LLM com contexto extraído e salva resultado")
    void processNote_callsLlmAndSaves() {
        Note pending = makeNote("projetos/adr-001.md", false);
        Note saved   = makeNote("projetos/adr-001.md", true);

        when(vault.findById("projetos/adr-001.md")).thenReturn(Optional.of(pending));
        when(llm.analyze(eq(NoteType.ADR), anyString())).thenReturn("### Análise\nConteúdo.");
        when(vault.save(any())).thenReturn(saved);

        Note result = service.processNote("projetos/adr-001.md");

        assertThat(result.isClaudeProcessed()).isTrue();
        verify(llm).analyze(eq(NoteType.ADR), contains("PostgreSQL"));
        verify(vault).save(any(Note.class));
    }

    @Test
    @DisplayName("processNote lança NoSuchElementException para nota inexistente")
    void processNote_throwsWhenNoteNotFound() {
        when(vault.findById(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processNote("inexistente.md"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("inexistente.md");
    }

    @Test
    @DisplayName("processNote lança IllegalStateException para nota já processada")
    void processNote_throwsWhenAlreadyProcessed() {
        Note processed = makeNote("projetos/adr-001.md", true);
        when(vault.findById("projetos/adr-001.md")).thenReturn(Optional.of(processed));

        assertThatThrownBy(() -> service.processNote("projetos/adr-001.md"))
            .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(llm);
    }

    @Test
    @DisplayName("processPending processa todas as notas pendentes")
    void processPending_processesAllPending() {
        Note n1 = makeNote("nota1.md", false);
        Note n2 = makeNote("nota2.md", false);
        Note saved = makeNote("nota1.md", true);

        when(vault.findPending()).thenReturn(List.of(n1, n2));
        when(vault.findById(anyString())).thenAnswer(inv ->
            Optional.of(makeNote(inv.getArgument(0), false)));
        when(llm.analyze(any(), anyString())).thenReturn("Análise.");
        when(vault.save(any())).thenReturn(saved);

        List<Note> results = service.processPending();

        assertThat(results).hasSize(2);
        verify(llm, times(2)).analyze(any(), anyString());
    }

    @Test
    @DisplayName("processPending ignora nota com erro e continua")
    void processPending_continuesOnError() {
        Note n1 = makeNote("boa.md", false);
        Note n2 = makeNote("ruim.md", false);

        when(vault.findPending()).thenReturn(List.of(n1, n2));
        when(vault.findById("boa.md")).thenReturn(Optional.of(n1));
        when(vault.findById("ruim.md")).thenReturn(Optional.empty());
        when(llm.analyze(any(), anyString())).thenReturn("ok");
        when(vault.save(any())).thenReturn(n1);

        List<Note> results = service.processPending();

        assertThat(results).hasSize(1);
    }
}
