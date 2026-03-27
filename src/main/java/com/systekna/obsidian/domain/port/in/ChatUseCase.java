package com.systekna.obsidian.domain.port.in;

import java.util.List;

/** Port de entrada: chat interativo com contexto do vault */
public interface ChatUseCase {
    String chat(String userMessage, List<String[]> history);
}
