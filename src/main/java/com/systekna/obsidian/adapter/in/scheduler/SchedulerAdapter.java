package com.systekna.obsidian.adapter.in.scheduler;

import com.systekna.obsidian.domain.model.Note;
import com.systekna.obsidian.domain.port.in.TemplateUseCase;
import com.systekna.obsidian.domain.port.out.SearchPort;
import com.systekna.obsidian.domain.port.out.VaultPort;
import com.systekna.obsidian.domain.port.out.LlmPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapter driving: jobs agendados com @Scheduled do Spring.
 * Processa notas pendentes e mantém o índice de busca atualizado.
 */
@Component
public class SchedulerAdapter {

    private final TemplateUseCase templateUseCase;
    private final VaultPort       vault;
    private final LlmPort         llm;
    private final SearchPort      searchPort;

    public SchedulerAdapter(TemplateUseCase templateUseCase,
                             VaultPort vault,
                             LlmPort llm,
                             SearchPort searchPort) {
        this.templateUseCase = templateUseCase;
        this.vault           = vault;
        this.llm             = llm;
        this.searchPort      = searchPort;
    }

    /** Processa todas as notas pendentes a cada 30 minutos */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void processPendingNotes() {
        System.out.println("[Scheduler] Iniciando processamento de notas pendentes...");
        List<Note> processed = templateUseCase.processPending();
        System.out.printf("[Scheduler] %d nota(s) processada(s).%n", processed.size());
    }

    /** Reindexação completa do vault toda meia-noite */
    @Scheduled(cron = "0 0 0 * * *")
    public void reindexVault() {
        System.out.println("[Scheduler] Reindexando vault completo...");
        List<Note> all = vault.findAll();
        for (Note note : all) {
            try {
                String text      = note.getTitle() + "\n" + note.extractContext();
                float[] embedding = llm.embed(text);
                searchPort.index(note.getId(), text, embedding);
            } catch (Exception e) {
                System.err.println("[Scheduler] Erro ao indexar " + note.getId() + ": " + e.getMessage());
            }
        }
        System.out.printf("[Scheduler] %d nota(s) indexada(s).%n", all.size());
    }
}
