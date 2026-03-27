package com.systekna.obsidian.config;

import com.systekna.obsidian.adapter.out.llm.ClaudeApiLlmAdapter;
import com.systekna.obsidian.adapter.out.search.SQLiteEmbeddingAdapter;
import com.systekna.obsidian.adapter.out.vault.FileSystemVaultAdapter;
import com.systekna.obsidian.application.ChatService;
import com.systekna.obsidian.application.SearchService;
import com.systekna.obsidian.application.TemplateService;
import com.systekna.obsidian.domain.port.in.ChatUseCase;
import com.systekna.obsidian.domain.port.in.SearchUseCase;
import com.systekna.obsidian.domain.port.in.TemplateUseCase;
import com.systekna.obsidian.domain.port.out.LlmPort;
import com.systekna.obsidian.domain.port.out.SearchPort;
import com.systekna.obsidian.domain.port.out.VaultPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;
import java.sql.SQLException;

/**
 * Composição da arquitetura hexagonal.
 * Único lugar que conhece todos os adapters — domínio não importa nada daqui.
 */
@Configuration
@EnableScheduling
public class AppConfig {

    @Value("${obsidian.vault.path}")
    private String vaultPath;

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Value("${obsidian.db.path:./obsidian-embeddings.db}")
    private String dbPath;

    // ─── Ports driven (infraestrutura) ───────────────────────────────────────

    @Bean
    public VaultPort vaultPort() {
        return new FileSystemVaultAdapter(Path.of(vaultPath));
    }

    @Bean
    public LlmPort llmPort() {
        return new ClaudeApiLlmAdapter(apiKey);
    }

    @Bean
    public SearchPort searchPort(VaultPort vaultPort) throws SQLException {
        return new SQLiteEmbeddingAdapter(dbPath, vaultPort);
    }

    // ─── Use cases (domínio / application) ───────────────────────────────────

    @Bean
    public TemplateUseCase templateUseCase(VaultPort vaultPort, LlmPort llmPort) {
        return new TemplateService(vaultPort, llmPort);
    }

    @Bean
    public SearchUseCase searchUseCase(LlmPort llmPort, SearchPort searchPort) {
        return new SearchService(llmPort, searchPort);
    }

    @Bean
    public ChatUseCase chatUseCase(LlmPort llmPort, SearchUseCase searchUseCase) {
        return new ChatService(llmPort, searchUseCase);
    }

    // ─── FileWatcher path ─────────────────────────────────────────────────────

    @Bean
    public Path vaultRootPath() {
        return Path.of(vaultPath);
    }
}
