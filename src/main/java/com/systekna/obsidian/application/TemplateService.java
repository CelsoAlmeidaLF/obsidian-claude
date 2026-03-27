package com.systekna.obsidian.application;

import com.systekna.obsidian.domain.model.Note;
import com.systekna.obsidian.domain.port.in.TemplateUseCase;
import com.systekna.obsidian.domain.port.out.LlmPort;
import com.systekna.obsidian.domain.port.out.VaultPort;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Use case de processamento de templates.
 * Orquestra VaultPort + LlmPort — sem dependência de framework.
 */
public class TemplateService implements TemplateUseCase {

    private final VaultPort vault;
    private final LlmPort llm;

    public TemplateService(VaultPort vault, LlmPort llm) {
        this.vault = vault;
        this.llm   = llm;
    }

    @Override
    public Note processNote(String noteId) {
        Note note = vault.findById(noteId)
            .orElseThrow(() -> new NoSuchElementException("Nota não encontrada: " + noteId));

        if (!note.hasProcessableContent()) {
            throw new IllegalStateException(
                "Nota '%s' já processada ou sem conteúdo suficiente.".formatted(noteId));
        }

        String context  = note.extractContext();
        String analysis = llm.analyze(note.getType(), context);
        Note updated    = note.withClaudeOutput(analysis);

        return vault.save(updated);
    }

    @Override
    public List<Note> processPending() {
        return vault.findPending()
            .stream()
            .filter(Note::hasProcessableContent)
            .map(note -> {
                try {
                    return processNote(note.getId());
                } catch (Exception e) {
                    // log e continua para a próxima nota
                    System.err.println("Erro ao processar " + note.getId() + ": " + e.getMessage());
                    return null;
                }
            })
            .filter(n -> n != null)
            .collect(Collectors.toList());
    }
}
