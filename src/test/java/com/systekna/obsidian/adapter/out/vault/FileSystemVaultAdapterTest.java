package com.systekna.obsidian.adapter.out.vault;

import com.systekna.obsidian.domain.model.Note;
import com.systekna.obsidian.domain.model.NoteType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FileSystemVaultAdapter — testes com TempDir")
class FileSystemVaultAdapterTest {

    @TempDir Path vault;

    FileSystemVaultAdapter adapter;

    private static final String PENDING_CONTENT = """
        ---
        tipo: adr
        claude_processado: false
        ---
        # ADR Banco de Dados

        ## Contexto
        <!-- claude:contexto -->
        Escolha entre PostgreSQL e MongoDB.

        ## 🤖 Análise Claude
        <!-- claude:output -->
        > *Seção automática.*
        """;

    private static final String PROCESSED_CONTENT = """
        ---
        tipo: daily-note
        claude_processado: true
        ---
        # Daily Note

        ## Notas
        <!-- claude:contexto -->
        Reunião sobre o projeto.
        """;

    @BeforeEach
    void setUp() {
        adapter = new FileSystemVaultAdapter(vault);
    }

    @Test
    @DisplayName("findAll retorna todas as notas .md do vault")
    void findAll_returnsAllMarkdownFiles() throws IOException {
        Files.writeString(vault.resolve("nota1.md"), PENDING_CONTENT);
        Files.writeString(vault.resolve("nota2.md"), PROCESSED_CONTENT);
        Files.createDirectory(vault.resolve("subpasta"));
        Files.writeString(vault.resolve("subpasta/nota3.md"), PENDING_CONTENT);

        List<Note> notes = adapter.findAll();
        assertThat(notes).hasSize(3);
    }

    @Test
    @DisplayName("findPending retorna apenas notas não processadas")
    void findPending_returnsOnlyUnprocessed() throws IOException {
        Files.writeString(vault.resolve("pendente.md"), PENDING_CONTENT);
        Files.writeString(vault.resolve("processada.md"), PROCESSED_CONTENT);

        List<Note> pending = adapter.findPending();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getId()).isEqualTo("pendente.md");
    }

    @Test
    @DisplayName("findById retorna nota correta pelo id")
    void findById_returnsCorrectNote() throws IOException {
        Files.writeString(vault.resolve("adr.md"), PENDING_CONTENT);

        Optional<Note> found = adapter.findById("adr.md");
        assertThat(found).isPresent();
        assertThat(found.get().getType()).isEqualTo(NoteType.ADR);
        assertThat(found.get().isClaudeProcessed()).isFalse();
        assertThat(found.get().getTitle()).isEqualTo("ADR Banco de Dados");
    }

    @Test
    @DisplayName("findById retorna empty para arquivo inexistente")
    void findById_returnsEmptyWhenNotFound() {
        assertThat(adapter.findById("inexistente.md")).isEmpty();
    }

    @Test
    @DisplayName("save persiste o conteúdo atualizado no disco")
    void save_persistsContent() throws IOException {
        Files.writeString(vault.resolve("adr.md"), PENDING_CONTENT);
        Note original = adapter.findById("adr.md").get();
        Note updated  = original.withClaudeOutput("### Análise\nConteúdo gerado.");

        adapter.save(updated);

        String diskContent = Files.readString(vault.resolve("adr.md"));
        assertThat(diskContent).contains("Conteúdo gerado.");
        assertThat(diskContent).contains("claude_processado: true");
    }

    @Test
    @DisplayName("save cria subdiretórios automaticamente")
    void save_createsSubdirectories() throws IOException {
        Files.writeString(vault.resolve("adr.md"), PENDING_CONTENT);
        Note original = adapter.findById("adr.md").get();

        // Simula salvar em subpasta que não existe ainda
        Note relocated = new Note("nova-pasta/adr.md", original.getTitle(),
            original.getType(), original.getRawContent(),
            original.getFrontmatter(), original.isClaudeProcessed(),
            original.getLastModified());

        assertThatNoException().isThrownBy(() -> adapter.save(relocated));
        assertThat(Files.exists(vault.resolve("nova-pasta/adr.md"))).isTrue();
    }

    @Test
    @DisplayName("exists retorna true para arquivo presente")
    void exists_trueWhenPresent() throws IOException {
        Files.writeString(vault.resolve("nota.md"), PENDING_CONTENT);
        assertThat(adapter.exists("nota.md")).isTrue();
        assertThat(adapter.exists("nao-existe.md")).isFalse();
    }
}
