package dev.dhruv.jobsearch.assistance;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenAiAssistanceProvider implements AssistanceProvider {

    private final String apiKey;
    private final String model;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public OpenAiAssistanceProvider(
            @Value("${app.assistance.openai.api-key:}") String apiKey,
            @Value("${app.assistance.openai.model:}") String model,
            @Value("${app.assistance.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.objectMapper = objectMapper;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override public boolean configured() { return !apiKey.isBlank() && !model.isBlank(); }
    @Override public String providerName() { return "OpenAI"; }
    @Override public String model() { return model.isBlank() ? "Not configured" : model; }

    @Override
    public ProviderResult generate(String instructions, String input, String schemaName, JsonNode schema) {
        if (!configured()) {
            throw new IllegalStateException("AI assistance is not configured. Set OPENAI_API_KEY and APP_OPENAI_MODEL, then restart the backend.");
        }
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", schemaName);
        format.put("strict", true);
        format.put("schema", schema);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("store", false);
        body.put("instructions", instructions);
        body.put("input", input);
        body.put("text", Map.of("format", format));
        body.put("max_output_tokens", 3000);

        String response = client.post().uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve().body(String.class);
        try {
            JsonNode root = objectMapper.readTree(response);
            String output = extractOutput(root);
            if (output == null || output.isBlank()) throw new IllegalStateException("The provider returned no structured output.");
            JsonNode usage = root.path("usage");
            return new ProviderResult(output, text(root, "id"), integer(usage, "input_tokens"), integer(usage, "output_tokens"));
        } catch (Exception exception) {
            throw new IllegalStateException("The provider response could not be read as schema-constrained output.", exception);
        }
    }

    private String extractOutput(JsonNode root) {
        String direct = text(root, "output_text");
        if (direct != null) return direct;
        JsonNode output = root.path("output");
        if (!output.isArray()) return null;
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) continue;
            for (JsonNode part : content) {
                String value = text(part, "text");
                if (value != null) return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.stringValue() : null;
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.intValue() : null;
    }
}
