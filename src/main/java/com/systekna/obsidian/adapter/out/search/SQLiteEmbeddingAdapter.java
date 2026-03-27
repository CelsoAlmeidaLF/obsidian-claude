package com.systekna.obsidian.adapter.out.search;

import com.systekna.obsidian.domain.model.Note;
import com.systekna.obsidian.domain.model.NoteType;
import com.systekna.obsidian.domain.model.SearchResult;
import com.systekna.obsidian.domain.port.out.SearchPort;
import com.systekna.obsidian.domain.port.out.VaultPort;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapter driven: persiste embeddings em SQLite e calcula similaridade cosine.
 * Utiliza HikariCP para pool otimizado de persistência em concorrência.
 */
public class SQLiteEmbeddingAdapter implements SearchPort {

    private final HikariDataSource dataSource;
    private final VaultPort vault;

    public SQLiteEmbeddingAdapter(String dbPath, VaultPort vault) throws SQLException {
        this.vault = vault;
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setMaximumPoolSize(10);
        config.setPoolName("SQLite-Embedding-Pool");
        // Em SQLite, auto-commit costuma ser requerido a menos que transações sejam explicitamente abertas.
        this.dataSource = new HikariDataSource(config);

        initSchema();
    }

    private void initSchema() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS embeddings (
                    note_id   TEXT PRIMARY KEY,
                    content   TEXT NOT NULL,
                    vector    BLOB NOT NULL,
                    indexed_at TEXT NOT NULL
                )
            """);
        }
    }

    @Override
    public void index(String noteId, String content, float[] embedding) {
        String sql = """
            INSERT OR REPLACE INTO embeddings(note_id, content, vector, indexed_at)
            VALUES (?, ?, ?, ?)
        """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, noteId);
            ps.setString(2, content);
            ps.setBytes(3, floatsToBytes(embedding));
            ps.setString(4, LocalDateTime.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao indexar nota: " + noteId, e);
        }
    }

    @Override
    public List<SearchResult> search(float[] queryEmbedding, int topK) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT note_id, content, vector FROM embeddings")) {

            ResultSet rs = ps.executeQuery();
            List<ScoredNote> scored = new ArrayList<>();

            while (rs.next()) {
                String noteId  = rs.getString("note_id");
                String content = rs.getString("content");
                float[] vec    = bytesToFloats(rs.getBytes("vector"));
                double score   = cosineSimilarity(queryEmbedding, vec);

                vault.findById(noteId).ifPresent(note ->
                    scored.add(new ScoredNote(note, score, excerpt(content))));
            }

            return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredNote::score).reversed())
                .limit(topK)
                .map(s -> new SearchResult(s.note(), s.score(), s.excerpt()))
                .collect(Collectors.toList());

        } catch (SQLException e) {
            throw new RuntimeException("Erro na busca semântica", e);
        }
    }

    @Override
    public void remove(String noteId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM embeddings WHERE note_id = ?")) {
            ps.setString(1, noteId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao remover embedding: " + noteId, e);
        }
    }

    @Override
    public void reindexAll() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("DELETE FROM embeddings");
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao limpar índice", e);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length == 0 || b.length == 0) return 0.0;
        int len = Math.min(a.length, b.length);
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < len; i++) {
            dot   += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0.0 : dot / denom;
    }

    private byte[] floatsToBytes(float[] floats) {
        byte[] bytes = new byte[floats.length * 4];
        for (int i = 0; i < floats.length; i++) {
            int bits = Float.floatToIntBits(floats[i]);
            bytes[i*4]   = (byte)(bits >> 24);
            bytes[i*4+1] = (byte)(bits >> 16);
            bytes[i*4+2] = (byte)(bits >> 8);
            bytes[i*4+3] = (byte)(bits);
        }
        return bytes;
    }

    private float[] bytesToFloats(byte[] bytes) {
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            int bits = ((bytes[i*4] & 0xFF) << 24)
                     | ((bytes[i*4+1] & 0xFF) << 16)
                     | ((bytes[i*4+2] & 0xFF) << 8)
                     |  (bytes[i*4+3] & 0xFF);
            floats[i] = Float.intBitsToFloat(bits);
        }
        return floats;
    }

    private String excerpt(String content) {
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }

    private record ScoredNote(Note note, double score, String excerpt) {}
}
