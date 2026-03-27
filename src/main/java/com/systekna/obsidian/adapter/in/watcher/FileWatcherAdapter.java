package com.systekna.obsidian.adapter.in.watcher;

import com.systekna.obsidian.domain.port.in.TemplateUseCase;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;

/**
 * Adapter driving: observa o vault com WatchService do JDK.
 * Dispara o TemplateUseCase quando um .md é criado ou modificado.
 */
@Component
public class FileWatcherAdapter implements Runnable {

    private final Path vaultRoot;
    private final TemplateUseCase templateUseCase;
    private volatile boolean running = false;

    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ScheduledFuture<?>> debounceMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);

    public FileWatcherAdapter(Path vaultRoot, TemplateUseCase templateUseCase) {
        this.vaultRoot       = vaultRoot;
        this.templateUseCase = templateUseCase;
    }

    public void start() {
        running = true;
        Thread thread = new Thread(this, "vault-file-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        scheduler.shutdown();
    }

    @Override
    public void run() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            registerAll(vaultRoot, watcher);

            while (running) {
                WatchKey key = watcher.poll(2, java.util.concurrent.TimeUnit.SECONDS);
                if (key == null) continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    Path changed = ((WatchKey) key).watchable() instanceof Path p
                        ? p.resolve((Path) event.context())
                        : null;

                    if (changed != null && changed.toString().endsWith(".md")) {
                        String noteId = vaultRoot.relativize(changed).toString();
                        handleChange(noteId);
                    }
                }
                key.reset();
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleChange(String noteId) {
        var existingTask = debounceMap.get(noteId);
        if (existingTask != null) existingTask.cancel(false);

        var newTask = scheduler.schedule(() -> {
            debounceMap.remove(noteId);
            try {
                templateUseCase.processNote(noteId);
            } catch (IllegalStateException e) {
                // Nota já processada ou sem conteúdo — ignorar silenciosamente
            } catch (Exception e) {
                System.err.println("[FileWatcher] Erro ao processar " + noteId + ": " + e.getMessage());
            }
        }, 500, java.util.concurrent.TimeUnit.MILLISECONDS);

        debounceMap.put(noteId, newTask);
    }

    private void registerAll(Path root, WatchService watcher) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs)
                throws IOException {
                dir.register(watcher,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
