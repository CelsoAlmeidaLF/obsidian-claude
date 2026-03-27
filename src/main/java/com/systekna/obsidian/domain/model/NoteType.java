package com.systekna.obsidian.domain.model;

/**
 * Tipos suportados de nota — mapeiam para o campo "tipo:" do frontmatter.
 */
public enum NoteType {
    DAILY_NOTE("daily-note"),
    ADR("adr"),
    ESTUDO_TECH("estudo-tech"),
    RETROSPECTIVA("retrospectiva"),
    UNKNOWN("unknown");

    private final String frontmatterValue;

    NoteType(String frontmatterValue) {
        this.frontmatterValue = frontmatterValue;
    }

    public static NoteType fromFrontmatter(String value) {
        if (value == null) return UNKNOWN;
        for (NoteType t : values()) {
            if (t.frontmatterValue.equalsIgnoreCase(value.trim())) return t;
        }
        return UNKNOWN;
    }

    public String getFrontmatterValue() { return frontmatterValue; }
}
