# Documentação: Obsidian-Claude Bridge

## Visão Geral
O **Obsidian-Claude Bridge** é uma aplicação intermediária que visa integrar cofres (vaults) locais do Obsidian diretamente com a API do modelo de linguagem Claude (Anthropic). 
Ele atua como um serviço em segundo plano (via JavaFX e Spring Boot) responsável por extrair contextos contextuais de notas, enviar comandos de IA para o Claude, e injetar automaticamente a resposta gerada diretamente dentro do arquivo markdown no Obsidian, tudo de forma assíncrona.

## Funcionalidades Principais
1. **Templates de Contexto no Obsidian**: Lendo seções exclusivas delimitadas por comentários HTML (`<!-- claude:contexto -->`) em arquivos `.md`.
2. **Atualização Automática (Injection)**: Inserção do output processado pelo LLM de volta no markdown na tag `<!-- claude:output -->`.
3. **Chat Assistente**: Um endpoint REST próprio de chat que recupera contexto de todo o vault para conversar com a IA.
4. **Busca Semântica**: Utiliza uma base de dados local SQLite (provavelmente alimentada por embeddings nativos) para encontrar respostas relevantes baseadas em similaridade semântica.
5. **Automação (Scheduler e Watcher)**: Possui adaptadores para monitorar as pastas e acionar rotinas periódicas para varrer notas a serem re-processadas.

## Arquitetura Hexagonal (Ports & Adapters)
O projeto foi totalmente refatorado sob o referencial de Arquitetura Hexagonal (Ports & Adapters) aliada ao design modular do Maven (`src/main/java`). A regra de ouro é: as dependências vão de fora para dentro. O Domínio é o núcleo agnóstico intocado.

### Camadas da Arquitetura
1. **Camada de Domínio (`domain`)**:
   - **`model`**: Agrega as entidades vitais (`Note`, `NoteType`, `SearchResult`). Possuem apenas regras de negócio textuais (ex: parsing do frontmatter, concatenação de output).
   - **`port/in`**: As portas (interfaces) que ditam os Casos de Uso que a Aplicação suporta (ex: `ChatUseCase`, `TemplateUseCase`, `SearchUseCase`).
   - **`port/out`**: Interfaces exigidas pelo domínio para realizar a persistência/computação externa (ex: `VaultPort`, `LlmPort`, `SearchPort`).

2. **Camada de Aplicação (`application`)**:
   - Implanta e orquestra a lógica dos Casos de Uso implementando as interfaces de entrada (`*UseCase`). Ex: `TemplateService`, `ChatService`. Dependem puramente das portas de saída inseridas via injeção de dependência.

3. **Camada de Infraestrutura/Adaptadores (`adapter`)**:
   - **Adaptadores Principais (in)**: Controladores REST (ex: `VaultController`), Watcher de pastas locais (`FileWatcherAdapter`) e tarefas agendadas (`SchedulerAdapter`). Ativam a aplicação como gatilhos externos.
   - **Adaptadores Secundários (out)**: Onde interações com sistemas periféricos tomam vida (ex: `ClaudeApiLlmAdapter` para viaxe REST Anthropic, `FileSystemVaultAdapter` na leitura real de disco, `SQLiteEmbeddingAdapter` consultando JDBC).

4. **Camada de Configuração (`config`)**:
   - O `AppConfig.java` funciona como o grande integrador. Ele injeta os "out-adapters" de fato nos Casos de Uso, semântica típica do Spring (`@Bean`, `@Configuration`). E o Spring Boot inicializado através de `ObsidianClaudeApp.java`.

### Compilação e Execução
Pré-requisitos: `Java 21` e `Maven`.
```bash
# Compilar e rodar testes de integração e unitários:
mvn clean test

# Rodar aplicação:
export ANTHROPIC_API_KEY="seu-token"
mvn spring-boot:run
```
O servidor sobe na porta 8080 (dependente do application.properties) suportando endpoints REST no namespace `/api/v1/`.
