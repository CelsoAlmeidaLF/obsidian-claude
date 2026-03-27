package com.systekna.obsidian.domain.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * Entidade central do domínio.
 * Representa uma nota do Obsidian com frontmatter e conteúdo Markdown.
 * Não depende de nenhum framework ou biblioteca externa.
 */
public class Note {

    private final String id;           // caminho relativo ao vault, ex: "projetos/adr-001.md"
    private final String title;
    private final NoteType type;
    private final String rawContent;   // conteúdo completo do arquivo .md
    private final Map<String, Object> frontmatter;
    private final boolean claudeProcessed;
    private final LocalDateTime lastModified;

    public Note(String id,
                String title,
                NoteType type,
                String rawContent,
                Map<String, Object> frontmatter,
                boolean claudeProcessed,
                LocalDateTime lastModified) {
        this.id             = Objects.requireNonNull(id, "id não pode ser nulo");
        this.title          = Objects.requireNonNull(title, "title não pode ser nulo");
        this.type           = Objects.requireNonNull(type, "type não pode ser nulo");
        this.rawContent     = Objects.requireNonNull(rawContent, "rawContent não pode ser nulo");
        this.frontmatter    = frontmatter != null ? Map.copyOf(frontmatter) : Map.of();
        this.claudeProcessed = claudeProcessed;
        this.lastModified   = lastModified;
    }

    /** Retorna o conteúdo das seções marcadas com <!-- claude:contexto --> */
    public String extractContext() {
        var sb   = new StringBuilder();
        var lines = rawContent.split("\n");
        boolean inContext = false;

        for (String line : lines) {
            if (line.contains("<!-- claude:contexto -->")) {
                inContext = true;
                continue;
            }
            if (line.contains("<!--") && inContext) {
                inContext = false;
                continue;
            }
            if (inContext && !line.isBlank()) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /** Verifica se a nota tem conteúdo suficiente para ser processada */
    public boolean hasProcessableContent() {
        return !claudeProcessed && extractContext().length() >= 20;
    }

    /** Cria uma nova Note com o output do Claude inserido e o flag atualizado */
    public Note withClaudeOutput(String output) {
        var updatedContent = insertClaudeOutput(rawContent, output);
        updatedContent = updatedContent.replaceFirst("claude_processado:\\s*false", "claude_processado: true");
        var updatedFrontmatter = new java.util.HashMap<>(frontmatter);
        updatedFrontmatter.put("claude_processado", true);
        return new Note(id, title, type, updatedContent, updatedFrontmatter, true, LocalDateTime.now());
    }

    private String insertClaudeOutput(String content, String output) {
        var timestamp = LocalDateTime.now().toString().replace("T", " ").substring(0, 16);
        var replacement = "<!-- claude:output -->\n> *Preenchido automaticamente em " + timestamp + ".*\n\n" + output + "\n\n";
        return content.replaceAll("(?s)<!--\\s*claude:output\\s*-->.*?(?=\\n##|\\n#|$)", replacement);
    }

    // Getters
    public String getId()               { return id; }
    public String getTitle()            { return title; }
    public NoteType getType()           { return type; }
    public String getRawContent()       { return rawContent; }
    public Map<String, Object> getFrontmatter() { return frontmatter; }
    public boolean isClaudeProcessed()  { return claudeProcessed; }
    public LocalDateTime getLastModified() { return lastModified; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Note note)) return false;
        return Objects.equals(id, note.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "Note{id='%s', type=%s, processed=%b}".formatted(id, type, claudeProcessed);
    }
}
