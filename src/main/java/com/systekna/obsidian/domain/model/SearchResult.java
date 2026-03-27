package com.systekna.obsidian.domain.model;

/**
 * Resultado de uma busca semântica no vault.
 * score: similaridade cosine (0.0 a 1.0)
 */
public record SearchResult(Note note, double score, String excerpt) {

    public SearchResult {
        if (score < 0.0 || score > 1.0)
            throw new IllegalArgumentException("score deve estar entre 0.0 e 1.0");
    }

    public boolean isRelevant() { return score >= 0.6; }
}
