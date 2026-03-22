package com.autogradingsystem.testcasegenerator.service;

/**
 * LLMConfig - LLM provider configuration for local Ollama (qwen2.5-coder:7b).
 *
 * PACKAGE: com.autogradingsystem.testcasegenerator.service
 *
 * Provider: Ollama running locally — no API key required.
 * Model:    qwen2.5-coder:7b
 *
 * Prerequisites:
 *   1. Install Ollama: https://ollama.com/download
 *   2. Pull the model: ollama pull qwen2.5-coder:7b
 *   3. Ollama must be running (it starts automatically on most installs,
 *      or run: ollama serve)
 *
 * The Ollama REST API is OpenAI-compatible at http://localhost:11434/v1/
 * No API key is needed; the Authorization header is omitted.
 */
public class LLMConfig {

    public static final String PROVIDER     = "openai"; // Ollama uses OpenAI-compatible format
    public static final String API_URL      = "http://localhost:11434/v1/chat/completions";
    public static final String MODEL        = "qwen2.5-coder:7b";
    public static final String ENV_KEY_NAME = ""; // no key needed for local Ollama
    public static final String API_VERSION  = "";

    public static final int MAX_TOKENS = 4096;
    public static final int TIMEOUT_S  = 180; // local models can be slower on first run

    // -------------------------------------------------------------------------
    // Key resolution — always returns empty for Ollama (no auth needed)
    // -------------------------------------------------------------------------

    public static String resolveApiKey() {
        return ""; // Ollama does not require an API key
    }

    // -------------------------------------------------------------------------
    // URL builder
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
        body.put("model",      MODEL);
        body.put("max_tokens", MAX_TOKENS);
        body.put("messages",   java.util.List.of(
                java.util.Map.of("role", "user", "content", prompt)));
        return mapper.writeValueAsString(body);
    }

    /**
     * Ollama does not support native PDF input.
     * PDF text is always extracted via PDFBox first and embedded in the prompt.
     * The base64Pdf parameter is accepted for interface compatibility but ignored.
     */
    public static String buildPdfPayload(String prompt, String base64Pdf, String pdfText,
            com.fasterxml.jackson.databind.ObjectMapper mapper) throws Exception {
        String combined = "=== EXAM PAPER TEXT ===\n" + pdfText + "\n\n=== TASK ===\n" + prompt;
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model",      MODEL);
        body.put("max_tokens", MAX_TOKENS);
        body.put("messages",   java.util.List.of(
                java.util.Map.of("role", "user", "content", combined)));
        return mapper.writeValueAsString(body);
    }

    // -------------------------------------------------------------------------
    // Response extractor — OpenAI-compatible format
    // -------------------------------------------------------------------------

    public static String extractText(String responseBody,
            com.fasterxml.jackson.databind.ObjectMapper mapper) throws Exception {
        // OpenAI-compatible: { choices: [ { message: { content: "..." } } ] }
        var root = mapper.readTree(responseBody);
        String text = root.path("choices").path(0).path("message").path("content").asText();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Empty response from Ollama: "
                    + responseBody.substring(0, Math.min(300, responseBody.length())));
        }
        return text;
    }

    // -------------------------------------------------------------------------
    // HTTP header builder — no Authorization header for Ollama
    // -------------------------------------------------------------------------

    public static java.net.http.HttpRequest.Builder addAuthHeaders(
            java.net.http.HttpRequest.Builder builder, String apiKey) {
        builder.header("Content-Type", "application/json");
        // Ollama does not require an Authorization header — intentionally omitted
        return builder;
    }
}
