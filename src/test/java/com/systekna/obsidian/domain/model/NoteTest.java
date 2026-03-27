package com.systekna.obsidian.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Note — entidade do domínio")
class NoteTest {

    private static final String CONTENT_WITH_CONTEXT = """
        ---
        tipo: adr
        claude_processado: false
        ---
        # Minha ADR

        ## Contexto
        <!-- claude:contexto -->
        Precisamos escolher entre PostgreSQL e MongoDB para o projeto X.
        O time tem mais experiência com SQL.

        ## Decisão
        <!-- claude:ignorar -->
        Escolhemos PostgreSQL.

        ## 🤖 Análise Claude
        <!-- claude:output -->
        > *Esta seção é preenchida automaticamente.*
        """;

    private Note buildNote(String content, boolean processed) {
        return new Note(
            "projetos/adr-001.md",
            "ADR 001",
            NoteType.ADR,
            content,
            Map.of("tipo", "adr", "claude_processado", processed),
            processed,
            LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("extractContext deve retornar apenas seções marcadas")
    void extractContext_returnOnlyMarkedSections() {
        Note note = buildNote(CONTENT_WITH_CONTEXT, false);
        String ctx = note.extractContext();

        assertTrue(ctx.contains("PostgreSQL"));
        assertTrue(ctx.contains("MongoDB"));
        assertFalse(ctx.contains("Escolhemos"), "seção ignorar não deve aparecer");
        assertFalse(ctx.contains("claude:output"), "marcador não deve aparecer no contexto");
    }

    @Test
    @DisplayName("hasProcessableContent retorna true para nota com contexto suficiente")
    void hasProcessableContent_trueWhenEnoughContext() {
        Note note = buildNote(CONTENT_WITH_CONTEXT, false);
        assertTrue(note.hasProcessableContent());
    }

    @Test
    @DisplayName("hasProcessableContent retorna false quando já processado")
    void hasProcessableContent_falseWhenAlreadyProcessed() {
        Note note = buildNote(CONTENT_WITH_CONTEXT, true);
        assertFalse(note.hasProcessableContent());
    }

    @Test
    @DisplayName("hasProcessableContent retorna false quando contexto vazio")
    void hasProcessableContent_falseWhenEmptyContext() {
        Note note = buildNote("# Nota sem contexto marcado", false);
        assertFalse(note.hasProcessableContent());
    }

    @Test
    @DisplayName("withClaudeOutput cria nova Note com flag processado = true")
    void withClaudeOutput_setsProcessedFlag() {
        Note original = buildNote(CONTENT_WITH_CONTEXT, false);
        Note updated  = original.withClaudeOutput("### Análise\nConteúdo gerado pelo Claude.");

        assertTrue(updated.isClaudeProcessed());
        assertFalse(original.isClaudeProcessed(), "original não deve ser mutado");
    }

    @Test
    @DisplayName("withClaudeOutput insere output no local correto")
    void withClaudeOutput_insertsOutputInCorrectPlace() {
        Note original = buildNote(CONTENT_WITH_CONTEXT, false);
        Note updated  = original.withClaudeOutput("### Análise\nResultado.");

        assertTrue(updated.getRawContent().contains("Resultado."));
        assertTrue(updated.getRawContent().contains("claude:output"));
    }

    @Test
    @DisplayName("igualdade baseada no id")
    void equality_basedOnId() {
        Note a = buildNote(CONTENT_WITH_CONTEXT, false);
        Note b = buildNote("outro conteúdo", true);
        assertEquals(a, b, "mesma nota se mesmo id");
    }

    @Test
    @DisplayName("construtor rejeita id nulo")
    void constructor_rejectsNullId() {
        assertThrows(NullPointerException.class, () ->
            new Note(null, "titulo", NoteType.ADR, "conteudo", Map.of(), false, LocalDateTime.now())
        );
    }
}
