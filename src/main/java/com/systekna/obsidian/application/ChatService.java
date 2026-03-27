package com.systekna.obsidian.application;

import com.systekna.obsidian.domain.model.SearchResult;
import com.systekna.obsidian.domain.port.in.ChatUseCase;
import com.systekna.obsidian.domain.port.in.SearchUseCase;
import com.systekna.obsidian.domain.port.out.LlmPort;
import com.systekna.obsidian.domain.port.out.SearchPort;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Use case de chat interativo com contexto do vault.
 */
public class ChatService implements ChatUseCase {

    private static final String SYSTEM_PROMPT = """
        Você é um assistente especializado no vault Obsidian do usuário.
        Responda sempre em português brasileiro.
        Seja objetivo e técnico, citando as notas relevantes quando aplicável.
        """;

    private final LlmPort llm;
    private final SearchUseCase search;

    public ChatService(LlmPort llm, SearchUseCase search) {
        this.llm    = llm;
        this.search = search;
    }

    @Override
    public String chat(String userMessage, List<String[]> history) {
        // Busca notas relevantes para enriquecer o contexto
        List<SearchResult> relevant = search.search(userMessage, 3);
        String contextBlock = buildContextBlock(relevant);

        String enrichedSystem = SYSTEM_PROMPT + "\n\nContexto do vault:\n" + contextBlock;
        return llm.chat(enrichedSystem, history, userMessage);
    }

    @Override
    public void chatStream(String userMessage, List<String[]> history, java.util.function.Consumer<String> onToken) {
        // Busca notas relevantes para enriquecer o contexto
        List<SearchResult> relevant = search.search(userMessage, 3);
        String contextBlock = buildContextBlock(relevant);

        String enrichedSystem = SYSTEM_PROMPT + "\n\nContexto do vault:\n" + contextBlock;
        llm.chatStream(enrichedSystem, history, userMessage, onToken);
    }

    private String buildContextBlock(List<SearchResult> results) {
        if (results.isEmpty()) return "(nenhuma nota relevante encontrada)";
        return results.stream()
            .filter(SearchResult::isRelevant)
            .map(r -> "## %s (score: %.2f)\n%s".formatted(
                r.note().getTitle(), r.score(), r.excerpt()))
            .collect(Collectors.joining("\n\n---\n\n"));
    }
}
