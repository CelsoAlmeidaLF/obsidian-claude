package com.systekna.obsidian.domain.port.in;

import com.systekna.obsidian.domain.model.SearchResult;
import java.util.List;

/** Port de entrada: busca semântica no vault */
public interface SearchUseCase {
    List<SearchResult> search(String query, int topK);
}
