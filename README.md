# Obsidian Claude Bridge

Aplicação Java que conecta seu vault Obsidian à API do Claude usando **Arquitetura Hexagonal (Ports & Adapters)**.

## Estrutura do projeto

```
com.systekna.obsidian
│
├── domain/                        ← DOMÍNIO (zero dependências externas)
│   ├── model/
│   │   ├── Note.java              ← Entidade principal
│   │   ├── NoteType.java          ← Enum de tipos de nota
│   │   └── SearchResult.java      ← Record de resultado de busca
│   ├── port/
│   │   ├── in/                    ← Driving ports (o mundo chama o domínio)
│   │   │   ├── TemplateUseCase.java
│   │   │   ├── ChatUseCase.java
│   │   │   └── SearchUseCase.java
│   │   └── out/                   ← Driven ports (o domínio chama o mundo)
│   │       ├── VaultPort.java
│   │       ├── LlmPort.java
│   │       └── SearchPort.java
│
├── application/                   ← CASOS DE USO (implementam os ports de entrada)
│   ├── TemplateService.java
│   ├── ChatService.java
│   └── SearchService.java
│
├── adapter/
│   ├── in/                        ← DRIVING ADAPTERS (acionam o domínio)
│   │   ├── rest/
│   │   │   └── VaultController.java    ← Spring REST
│   │   ├── watcher/
│   │   │   └── FileWatcherAdapter.java ← WatchService JDK
│   │   └── scheduler/
│   │       └── SchedulerAdapter.java   ← @Scheduled Spring
│   └── out/                       ← DRIVEN ADAPTERS (implementam ports de saída)
│       ├── llm/
│       │   └── ClaudeApiLlmAdapter.java
│       ├── vault/
│       │   └── FileSystemVaultAdapter.java
│       └── search/
│           └── SQLiteEmbeddingAdapter.java
│
└── config/
    └── AppConfig.java             ← Composição: conecta ports ↔ adapters
```

## Regra de dependência

```
adapter/in → application → domain ← adapter/out
```

O domínio **nunca** importa nada de `adapter` ou `config`. Toda dependência aponta para dentro.

## Pré-requisitos

- Java 21+
- Maven 3.9+
- Variável de ambiente: `ANTHROPIC_API_KEY`

## Configuração

Edite `src/main/resources/application.properties`:

```properties
obsidian.vault.path=/caminho/do/seu/vault
obsidian.db.path=./obsidian-embeddings.db
anthropic.api.key=${ANTHROPIC_API_KEY}
server.port=8080
```

## Executar

```bash
export ANTHROPIC_API_KEY="sua-chave-aqui"
mvn spring-boot:run
```

## Rodar os testes

```bash
mvn test
```

## Endpoints REST

| Método | Endpoint                          | Descrição                          |
|--------|-----------------------------------|------------------------------------|
| POST   | `/api/v1/notes/{id}/process`      | Processa uma nota específica       |
| POST   | `/api/v1/notes/process-pending`   | Processa todas as notas pendentes  |
| POST   | `/api/v1/chat`                    | Chat com contexto do vault         |
| GET    | `/api/v1/search?q=query&limit=5`  | Busca semântica                    |

### Exemplo: chat
```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "qual banco escolhemos para o projeto X?", "history": []}'
```

### Exemplo: busca semântica
```bash
curl "http://localhost:8080/api/v1/search?q=arquitetura+hexagonal&limit=3"
```

## Funcionamento dos templates

As notas do vault devem conter marcações especiais nos comentários HTML:

- `<!-- claude:contexto -->` — seção enviada para o Claude como input
- `<!-- claude:output -->` — seção onde o Claude escreve a análise
- `<!-- claude:ignorar -->` — seção privada, nunca processada

## Cobertura de testes

| Camada | Arquivo de teste | Tipo |
|--------|-----------------|------|
| Domínio | `NoteTest` | Unitário |
| Use case | `TemplateServiceTest` | Unitário (mocks) |
| Use case | `ChatAndSearchServiceTest` | Unitário (mocks) |
| Adapter in | `VaultControllerTest` | Integração (MockMvc) |
| Adapter out | `FileSystemVaultAdapterTest` | Integração (TempDir) |
