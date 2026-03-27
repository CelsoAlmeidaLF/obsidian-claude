package com.systekna.obsidian.domain.port.out;

import com.systekna.obsidian.domain.model.SearchResult;
import java.util.List;

/**
 * Port de saída: indexação e busca semântica.
 * Implementado pelo SQLiteEmbeddingAdapter.
 */
public interface SearchPort {
    void index(String noteId, String content, float[] embedding);
    List<SearchResult> search(float[] queryEmbedding, int topK);
    void remove(String noteId);
    void reindexAll();
}
