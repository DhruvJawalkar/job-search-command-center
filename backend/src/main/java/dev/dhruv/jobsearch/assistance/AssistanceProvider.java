package dev.dhruv.jobsearch.assistance;

import tools.jackson.databind.JsonNode;

public interface AssistanceProvider {
    boolean configured();
    String providerName();
    String model();
    ProviderResult generate(String instructions, String input, String schemaName, JsonNode schema);

    record ProviderResult(String outputJson, String responseId, Integer inputTokens, Integer outputTokens) {}
}
