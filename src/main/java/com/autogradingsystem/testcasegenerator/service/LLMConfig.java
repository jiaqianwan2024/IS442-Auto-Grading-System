package com.autogradingsystem.testcasegenerator.service;

/**
 * LLMConfig - LLM provider configuration for Cohere API.
 *
 * PACKAGE: com.autogradingsystem.testcasegenerator.service
 *
 * Provider: Cohere via OpenAI-compatible endpoint
 * Free key: https://dashboard.cohere.com/api-keys
 *
 * Set in .env: COHERE_API_KEY=your-key-here
 */
public class LLMConfig {

    public static final String PROVIDER     = "openai"; // Cohere uses OpenAI-compatible format
    public static final String API_URL      = "https://api.cohere.com/compatibility/v1/chat/completions";
    public static final String MODEL        = "command-r-plus-08-2024";
    public static final String ENV_KEY_NAME = "COHERE_API_KEY";
    public static final String API_VERSION  = "";

    public static final int MAX_TOKENS = 4096;
    public static final int TIMEOUT_S  = 120;

    // -------------------------------------------------------------------------
    // Key resolution
    // -------------------------------------------------------------------------

    public static String resolveApiKey() {
        if (ENV_KEY_NAME == null || ENV_KEY_NAME.isBlank()) return "";
        String key = System.getenv(ENV_KEY_NAME);
        if (key == null || key.isBlank()) key = System.getProperty(ENV_KEY_NAME);
        if (key == null || key.isBlank()) {
            System.err.println("⚠️  COHERE_API_KEY is not set.");
            System.err.println("    Add COHERE_API_KEY=your-key to your .env file.");
            System.err.println("    Get a free key at: https://dashboard.cohere.com/api-keys");
            return "";
        }
        return key;
    }

    // -------------------------------------------------------------------------
    // URL builder — kept for compatibility, Cohere uses plain URL
    // -------------------------------------------------------------------------

    public static String buildUrl(String apiKey) {
        return API_URL;
    }

    // -------------------------------------------------------------------------
    // Request builders
    // -------------------------------------------------------------------------

    public static String buildTextPayload(String prompt,
            com.fasterxml.jackson.databind.ObjectMapper mapper) throws Exception {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model",    MODEL);
        body.put("max_tokens", MAX_TOKENS);
        body.put("messages", java.util.List.of(
                java.util.Map.of("role", "user", "content", prompt)));
        return mapper.writeValueAsString(body);
    }

    public static String buildPdfPayload(String prompt, String base64Pdf, String pdfText,
            com.fasterxml.jackson.databind.ObjectMapper mapper) throws Exception {
        // Cohere does not support native PDF — embed extracted PDF text in prompt
        String combined = "=== EXAM PAPER TEXT ===\n" + pdfText + "\n\n=== TASK ===\n" + prompt;
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model",    MODEL);
        body.put("max_tokens", MAX_TOKENS);
        body.put("messages", java.util.List.of(
                java.util.Map.of("role", "user", "content", combined)));
        return mapper.writeValueAsString(body);
    }

    // -------------------------------------------------------------------------
    // Response extractor
    // -------------------------------------------------------------------------

    public static String extractText(String responseBody,
            com.fasterxml.jackson.databind.ObjectMapper mapper) throws Exception {
        // OpenAI-compatible: { choices: [ { message: { content: "..." } } ] }
        var root = mapper.readTree(responseBody);
        String text = root.path("choices").path(0).path("message").path("content").asText();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Empty response from Cohere: "
                    + responseBody.substring(0, Math.min(300, responseBody.length())));
        }
        return text;
    }

    // -------------------------------------------------------------------------
    // HTTP header builder
    // -------------------------------------------------------------------------

    public static java.net.http.HttpRequest.Builder addAuthHeaders(
            java.net.http.HttpRequest.Builder builder, String apiKey) {
        builder.header("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder;
    }
}