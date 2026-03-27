package com.systekna.obsidian.adapter.out.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.systekna.obsidian.domain.model.NoteType;
import com.systekna.obsidian.domain.port.out.LlmPort;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Adapter driven: implementa LlmPort chamando a API do Claude.
 * O domínio não sabe que existe Anthropic, OkHttp ou JSON aqui.
 */
public class ClaudeApiLlmAdapter implements LlmPort {

    private static final String API_URL   = "https://api.anthropic.com/v1/messages";
    private static final String MODEL     = "claude-sonnet-4-20250514";
    private static final String API_VER   = "2023-06-01";
    private static final MediaType JSON   = MediaType.get("application/json");

    private static final Map<NoteType, String> SYSTEM_PROMPTS = Map.of(
        NoteType.DAILY_NOTE, """
            Você é um assistente de produtividade analisando uma daily note.
            Preencha as subseções de "🤖 Análise Claude": Resumo do dia, Padrões identificados,
            Sugestões para amanhã e Conexões com outras notas. Seja direto, máximo 3 linhas cada.""",

        NoteType.ADR, """
            Você é um arquiteto de software sênior revisando uma ADR.
            Preencha: Avaliação da decisão, Riscos não considerados,
            Alternativas que merecem atenção, Pontos a monitorar, ADRs relacionados.""",

        NoteType.ESTUDO_TECH, """
            Você é um mentor técnico revisando anotações de estudo.
            Preencha: Mapa de conceitos, Lacunas identificadas, Próximos tópicos,
            Conexões com C#/Node.js/Python/Java, Recursos para aprofundar.""",

        NoteType.RETROSPECTIVA, """
            Você é um Scrum Master experiente analisando uma retrospectiva.
            Preencha: Padrões recorrentes, Riscos que merecem atenção,
            Sugestões de processo, Métricas a acompanhar, Conexões com ADRs."""
    );

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final String apiKey;

    public ClaudeApiLlmAdapter(String apiKey) {
        this.apiKey = apiKey;
        this.mapper = new ObjectMapper();
        this.http   = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String analyze(NoteType type, String context) {
        String system = SYSTEM_PROMPTS.getOrDefault(type, SYSTEM_PROMPTS.get(NoteType.ADR));
        String user   = "Aqui está o conteúdo da nota:\n\n" + context;
        return callApi(system, List.of(), user, 1500);
    }

    @Override
    public String chat(String systemPrompt, List<String[]> history, String userMessage) {
        return callApi(systemPrompt, history, userMessage, 2000);
    }

    @Override
    public void chatStream(String systemPrompt, List<String[]> history, String userMessage, java.util.function.Consumer<String> onToken) {
        callApiStream(systemPrompt, history, userMessage, 2000, onToken);
    }

    @Override
    public float[] embed(String text) {
        // Nota: a API de embeddings do Claude ainda não está disponível publicamente.
        // Implementação com embeddings via API de mensagens (representação simplificada).
        // Para produção, substitua por um modelo de embeddings dedicado (ex: OpenAI ada-002).
        String prompt = "Gere uma representação semântica resumida para: " + text;
        String response = callApi("Responda apenas com números separados por vírgula (128 valores entre -1 e 1).",
            List.of(), prompt, 200);
        return parseEmbedding(response);
    }

    private String callApi(String system, List<String[]> history, String userMessage, int maxTokens) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", MODEL);
            body.put("max_tokens", maxTokens);
            body.put("system", system);

            ArrayNode messages = body.putArray("messages");
            for (String[] turn : history) {
                messages.addObject().put("role", turn[0]).put("content", turn[1]);
            }
            messages.addObject().put("role", "user").put("content", userMessage);

            Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", API_VER)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .build();

            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Claude API erro " + response.code() + ": " + response.body().string());
                }
                JsonNode json = mapper.readTree(response.body().string());
                return json.at("/content/0/text").asText();
            }
        } catch (IOException e) {
            throw new RuntimeException("Falha ao chamar Claude API", e);
        }
    }

    private void callApiStream(String system, List<String[]> history, String userMessage, int maxTokens, java.util.function.Consumer<String> onToken) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", MODEL);
            body.put("max_tokens", maxTokens);
            body.put("system", system);
            body.put("stream", true);

            ArrayNode messages = body.putArray("messages");
            for (String[] turn : history) {
                messages.addObject().put("role", turn[0]).put("content", turn[1]);
            }
            messages.addObject().put("role", "user").put("content", userMessage);

            Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", API_VER)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .build();

            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Claude API erro " + response.code() + ": " + response.body().string());
                }
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body().byteStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if (data.equals("[DONE]")) break;
                            JsonNode chunk = mapper.readTree(data);
                            if (chunk.has("type") && "content_block_delta".equals(chunk.get("type").asText())) {
                                onToken.accept(chunk.at("/delta/text").asText());
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Falha ao chamar Claude API stream", e);
        }
    }

    private float[] parseEmbedding(String raw) {
        try {
            String[] parts = raw.replaceAll("[^0-9.,\\-]", "").split(",");
            float[] result = new float[Math.min(parts.length, 128)];
            for (int i = 0; i < result.length; i++) {
                result[i] = Float.parseFloat(parts[i].trim());
            }
            return result;
        } catch (Exception e) {
            return new float[128]; // fallback: vetor zero
        }
    }
}
