package com.systekna.obsidian.application;

import com.systekna.obsidian.domain.model.SearchResult;
import com.systekna.obsidian.domain.port.in.SearchUseCase;
import com.systekna.obsidian.domain.port.out.LlmPort;
import com.systekna.obsidian.domain.port.out.SearchPort;

import java.util.List;

/**
 * Use case de busca semântica — gera embedding da query e consulta o store.
 */
public class SearchService implements SearchUseCase {

    private final LlmPort llm;
    private final SearchPort searchStore;

    public SearchService(LlmPort llm, SearchPort searchStore) {
        this.llm         = llm;
        this.searchStore = searchStore;
    }

    @Override
    public List<SearchResult> search(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        float[] embedding = llm.embed(query);
        return searchStore.search(embedding, topK);
    }
}
