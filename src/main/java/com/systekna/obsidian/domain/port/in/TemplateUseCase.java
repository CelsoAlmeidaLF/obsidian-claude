package com.systekna.obsidian.domain.port.in;

import com.systekna.obsidian.domain.model.Note;
import java.util.List;

/** Port de entrada: processamento de templates */
public interface TemplateUseCase {
    Note processNote(String noteId);
    List<Note> processPending();
}
