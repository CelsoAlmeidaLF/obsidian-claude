package com.systekna.obsidian.domain.port.out;

import com.systekna.obsidian.domain.model.Note;
import java.util.List;
import java.util.Optional;

/**
 * Port de saída: tudo que o domínio precisa do vault.
 * Implementado pelos adapters de filesystem e REST do Obsidian.
 */
public interface VaultPort {
    List<Note> findPending();                    // notas com claude_processado: false
    Optional<Note> findById(String noteId);
    List<Note> findAll();
    Note save(Note note);                        // persiste o conteúdo atualizado
    boolean exists(String noteId);
}
