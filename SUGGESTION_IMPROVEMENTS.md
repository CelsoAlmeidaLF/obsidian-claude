# Sugestões de Melhorias: Obsidian-Claude

A aplicação atual é robusta em seu design hexagonal, promovendo extrema flexibilidade na troca de ferramentas. Visando escalabilidade e estabilidade sistêmica em cenários de uso intensivo diário por usuários, eis dicas técnicas avançadas para o roadmap futuro:

## 1. Melhorias Arquiteturais e de Código
- **Melhoria no Monitoramento de Arquivos (`FileWatcherAdapter`)**: Depender exclusivamente do Java NIO `WatchService` costuma ser problemático em multiplataformas. Ele pode causar atrasos excessivos (polling de polling, ex: MacOS) ou disparar eventos redobrados para o mesmo arquivo (duplo `save`). Recomenda-se utilizar bibliotecas especializadas como o _Apache Commons VFS_ ou o _MacDirectoryWatcher_ para maior estabilidade, combinadas a uma estratégia de _debounce_ nos registros das chaves do arquivo.
- **Isolamento Total do Frontmatter**: O parser caseiro baseado no `.split()` ou indexação ingênua pode falhar mediante sintaxe flexível do YAML (como espaçamento diferente). Recomenda-se passar o documento textualmente por um scanner consolidado de Markdown (ex: `flexmark-java` ou extrações de blocos avançadas no Jackson YAML).
- **Graceful Error Handling REST**: Expandir o `VaultController` com um `@ControllerAdvice` global, respondendo todas as inconsistências não mapeadas com uma classe envelopada padrão de erros unânime em JSON.

## 2. Abordagem de Performance e Banco (SQLite)
- **HikariCP no SQLite**: A comunicação direta no modelo JDBC cru `SQLiteEmbeddingAdapter` (abre-e-fecha instanciado em escopo de requisição) drena a CPU. Introduzir o pool de conexões (ex: Hikari) voltado para bancos embarcados aumentará vastamente o throughput concorrente ao atender pesquisas.
- **Tuning de Banco Vetorial Externo**: Caso o vault seja enorme (> 2.000 notas técnicas), consultar similaridade com embeddings via SQL plano (funções matemáticas UDF SQLite) não é escalável. Seria mais veloz introduzir no domínio um `Adapter` suportando o **PGVector** (PostgreSQL) livre, Qdrant ou **ChromaDB**. Pelo pattern Hexagonal, isso é meramente substituir o `@Bean SearchPort`.

## 3. Melhorias na Experiência do Usuário (UX/Features)
- **Streaming de Respostas via Server-Sent Events (SSE)**: Hoje as requisições `POST /api/v1/chat` bloqueiam toda a Thread aguardando o Claude terminar sua elaboração, sujeitas ao tempo de timeout do TCP em inferências de text-block altíssimos. Modificar os endpoints e Casos de Uso para retornar um `Flux<String>` (WebFlux) ou `SseEmitter` exibirá as respostas sendo redigidas instantaneamente no plugin interface Obsidian.
- **Suporte para Modelos Alternativos (OpenAI, Local LLMs)**: Com a `LlmPort` isolada, o projeto ganha se ofertar não só o Claude API, mas também adaptadores modulares para rodar modelos abertos offline (Llama 3 com base no `OllamaApiAdapter`). O usuário configuraria `llm.provider=local` e nada cobraria tarifa externa na API.
- **Diff/Backup Histórico em Arquivos Alterados**: Como o backend Java injeta texto editando diretamente os arquivos de disco do usuário de forma autônoma sem versionamento, a corrupção textual se torna crítica caso algo falhe entre a leitura/gravação. O `FileSystemVaultAdapter` deveria gravar um cache local invisível (uma pasta oculta `.bridge-backups`) da nota _antes_ da manipulação por precaução contra perda de dados do usuário.
