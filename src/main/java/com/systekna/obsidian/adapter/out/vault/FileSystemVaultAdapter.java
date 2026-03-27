package com.systekna.obsidian.adapter.out.vault;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.systekna.obsidian.domain.model.Note;
import com.systekna.obsidian.domain.model.NoteType;
import com.systekna.obsidian.domain.port.out.VaultPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapter driven: lê e escreve arquivos .md diretamente no filesystem.
 * Implementa VaultPort sem que o domínio saiba que é disco.
 */
public class FileSystemVaultAdapter implements VaultPort {

    private final Path vaultRoot;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public FileSystemVaultAdapter(Path vaultRoot) {
        this.vaultRoot = vaultRoot;
    }

    @Override
    public List<Note> findPending() {
        return findAll().stream()
            .filter(n -> !n.isClaudeProcessed())
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Note> findById(String noteId) {
        Path file = vaultRoot.resolve(noteId);
        if (!Files.exists(file)) return Optional.empty();
        return Optional.of(parseNote(noteId, file));
    }

    @Override
    public List<Note> findAll() {
        try {
            List<Note> notes = new ArrayList<>();
            Files.walkFileTree(vaultRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".md")) {
                        String id = vaultRoot.relativize(file).toString();
                        notes.add(parseNote(id, file));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return notes;
        } catch (IOException e) {
            throw new RuntimeException("Erro ao listar vault: " + vaultRoot, e);
        }
    }

    @Override
    public Note save(Note note) {
        Path file = vaultRoot.resolve(note.getId());
        try {
            if (Files.exists(file)) {
                Path backupDir = vaultRoot.resolve(".bridge-backups");
                Files.createDirectories(backupDir);
                
                String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String backupFilename = file.getFileName().toString().replace(".md", "_" + timestamp + ".md");
                Path backupFile = backupDir.resolve(backupFilename);
                Files.copy(file, backupFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.createDirectories(file.getParent());
            }

            Files.writeString(file, note.getRawContent(), StandardCharsets.UTF_8);
            return note;
        } catch (IOException e) {
            throw new RuntimeException("Erro ao salvar nota: " + note.getId(), e);
        }
    }

    @Override
    public boolean exists(String noteId) {
        return Files.exists(vaultRoot.resolve(noteId));
    }

    // ─── helpers privados ────────────────────────────────────────────────────

    private Note parseNote(String id, Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, Object> frontmatter = extractFrontmatter(content);
            String tipo = (String) frontmatter.getOrDefault("tipo", "unknown");
            boolean processed = Boolean.TRUE.equals(frontmatter.get("claude_processado"));
            String title = extractTitle(content, id);
            LocalDateTime modified = Files.getLastModifiedTime(file)
                .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

            return new Note(id, title, NoteType.fromFrontmatter(tipo),
                content, frontmatter, processed, modified);

        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler nota: " + id, e);
        }
    }

    private Map<String, Object> extractFrontmatter(String content) {
        if (!content.startsWith("---")) return Map.of();
        int end = content.indexOf("---", 3);
        if (end < 0) return Map.of();
        String yamlBlock = content.substring(3, end).trim();
        try {
            Map<String, Object> parsed = yamlMapper.readValue(yamlBlock, new TypeReference<>() {});
            return parsed != null ? parsed : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String extractTitle(String content, String fallbackId) {
        return content.lines()
            .filter(l -> l.startsWith("# "))
            .map(l -> l.substring(2).trim())
            .findFirst()
            .orElse(fallbackId);
    }
}
