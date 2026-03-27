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
 * Adapter driven: implementa LlmPort chamando a API local do Ollama no device.
 */
public class OllamaApiAdapter implements LlmPort {

    private final String apiUrl;
    private final String model;
    private static final MediaType JSON = MediaType.get("application/json");

    private static final Map<NoteType, String> SYSTEM_PROMPTS = Map.of(
        NoteType.DAILY_NOTE, "Você é um assistente de produtividade analisando uma daily note.\n" +
                             "Preencha as subseções de 'Análise': Resumo do dia, Padrões identificados,\n" +
                             "Sugestões para amanhã e Conexões com outras notas. Seja direto, máximo 3 linhas cada.",

        NoteType.ADR, "Você é um arquiteto de software sênior revisando uma ADR.\n" +
                      "Preencha: Avaliação da decisão, Riscos não considerados,\n" +
                      "Alternativas que merecem atenção, Pontos a monitorar, ADRs relacionados.",

        NoteType.ESTUDO_TECH, "Você é um mentor técnico revisando anotações de estudo.\n" +
                              "Preencha: Mapa de conceitos, Lacunas identificadas, Próximos tópicos,\n" +
                              "Conexões de linguagens, Recursos para aprofundar.",

        NoteType.RETROSPECTIVA, "Você é um Scrum Master experiente analisando uma retrospectiva.\n" +
                                "Preencha: Padrões recorrentes, Riscos que merecem atenção,\n" +
                                "Sugestões de processo, Métricas a acompanhar, Conexões com ADRs."
    );

    private final OkHttpClient http;
    private final ObjectMapper mapper;

    public OllamaApiAdapter(String apiUrl, String model) {
        this.apiUrl = apiUrl;
        this.model  = model;
        this.mapper = new ObjectMapper();
        this.http   = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS) // Modelos locais demandam mais tempo de resposta
            .build();
    }

    @Override
    public String analyze(NoteType type, String context) {
        String system = SYSTEM_PROMPTS.getOrDefault(type, SYSTEM_PROMPTS.get(NoteType.ADR));
        String user   = "Aqui está o conteúdo da nota:\n\n" + context;
        return callApi(system, List.of(), user);
    }

    @Override
    public String chat(String systemPrompt, List<String[]> history, String userMessage) {
        return callApi(systemPrompt, history, userMessage);
    }

    @Override
    public void chatStream(String systemPrompt, List<String[]> history, String userMessage, java.util.function.Consumer<String> onToken) {
        callApiStream(systemPrompt, history, userMessage, onToken);
    }

    @Override
    public float[] embed(String text) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("prompt", "Gere embeddings para: " + text);

            Request request = new Request.Builder()
                .url(apiUrl.replace("/api/chat", "/api/embeddings"))
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .build();

            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful()) return new float[128];
                JsonNode json = mapper.readTree(response.body().string());
                JsonNode embedNode = json.get("embedding");
                if (embedNode != null && embedNode.isArray()) {
                    int len = Math.min(embedNode.size(), 128);
                    float[] result = new float[len];
                    for (int i = 0; i < len; i++) {
                        result[i] = (float) embedNode.get(i).asDouble();
                    }
                    return result;
                }
            }
        } catch (Exception e) {}
        return new float[128];
    }

    private String callApi(String system, List<String[]> history, String userMessage) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("stream", false);

            ArrayNode messages = body.putArray("messages");
            if (system != null && !system.isBlank()) {
                messages.addObject().put("role", "system").put("content", system);
            }
            for (String[] turn : history) {
                messages.addObject().put("role", turn[0]).put("content", turn[1]);
            }
            messages.addObject().put("role", "user").put("content", userMessage);

            Request request = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .build();

            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Ollama API erro " + response.code() + ": " + response.body().string());
                }
                JsonNode json = mapper.readTree(response.body().string());
                return json.at("/message/content").asText();
            }
        } catch (IOException e) {
            throw new RuntimeException("Falha ao chamar Ollama API", e);
        }
    }

    private void callApiStream(String system, List<String[]> history, String userMessage, java.util.function.Consumer<String> onToken) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("stream", true);

            ArrayNode messages = body.putArray("messages");
            if (system != null && !system.isBlank()) {
                messages.addObject().put("role", "system").put("content", system);
            }
            for (String[] turn : history) {
                messages.addObject().put("role", turn[0]).put("content", turn[1]);
            }
            messages.addObject().put("role", "user").put("content", userMessage);

            Request request = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .build();

            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Ollama API erro " + response.code() + ": " + response.body().string());
                }
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body().byteStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) continue;
                        JsonNode chunk = mapper.readTree(line);
                        if (chunk.has("message") && chunk.get("message").has("content")) {
                            onToken.accept(chunk.get("message").get("content").asText());
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Falha ao chamar Ollama API stream", e);
        }
    }
}
