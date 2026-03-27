package com.systekna.obsidian.domain.port.out;

import com.systekna.obsidian.domain.model.Note;
import com.systekna.obsidian.domain.model.NoteType;

/**
 * Port de saída: comunicação com o modelo de linguagem.
 * Implementado pelo ClaudeApiLlmAdapter.
 */
public interface LlmPort {
    String analyze(NoteType type, String context);
    String chat(String systemPrompt, java.util.List<String[]> history, String userMessage);
    float[] embed(String text);                  // gera embedding para busca semântica
}
