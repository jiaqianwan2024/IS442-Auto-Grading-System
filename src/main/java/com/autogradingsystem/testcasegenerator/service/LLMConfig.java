package com.autogradingsystem.testcasegenerator.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpRequest;
import java.util.*;

/**
 * LLMConfig - LLM provider configuration for Ollama (local inference).
 *
 * PACKAGE: com.autogradingsystem.testcasegenerator.service
 *
 * Provider: Ollama running locally on http://localhost:11434
 * Model:    qwen2.5-coder:7b  (best local model for Java code tasks)
 *
 * WHY OLLAMA + QWEN2.5-CODER:7B:
 *   - Runs entirely on CPU — no GPU required (slower but functional)
 *   - Fits in ~5GB RAM when quantised (Q4_K_M, the Ollama default)
 *   - qwen2.5-coder is specifically trained on code; strong Java output
 *   - Ollama supports native JSON mode — same reliability improvement as Gemini
 *   - No API key, no usage limits, no internet required during grading
 *   - Single model handles all three tasks: PDF parsing, Java reasoning,
 *     structured JSON output
 *
 * SETUP (one-time):
 *   1. Download Ollama: https://ollama.com/download
 *   2. Pull the model (downloads ~4.7GB):
 *        ollama pull qwen2.5-coder:7b
 *   3. Ollama starts automatically — no manual server launch needed
 *
 * PERFORMANCE EXPECTATIONS ON A LAPTOP (no discrete GPU):
 *   - ~10-30 tokens/second on a modern CPU (M-series Mac or recent Intel/AMD)
 *   - ~15-45 seconds per question for test case generation
 *   - Full grading run for 5 questions: ~2-4 minutes total
 *
 * UPGRADING TO A BETTER MODEL LATER:
 *   Change MODEL to any Ollama-compatible model:
 *     "qwen2.5-coder:14b"    — better quality, needs ~10GB RAM
 *     "deepseek-coder-v2"    — strong on code, ~8GB RAM
 *     "llama3.1:8b"          — good general reasoning, ~5GB RAM
 *   Then run: ollama pull <model-name>
 *   No other code changes needed.
 *
 * PDF SUPPORT:
 *   Ollama does not support native PDF input. ExamPaperParser will fall back
 *   to PDFBox text extraction automatically (pdfText parameter is used instead
 *   of base64Pdf). This works fine for text-based PDFs. For scanned PDFs,
 *   consider switching to the Gemini provider (LLMConfig_Gemini.java).
 *
 * NO API KEY REQUIRED — ENV_KEY_NAME is intentionally blank.
 */
public class LLMConfig {

    public static final String PROVIDER     = "openai"; // Ollama uses OpenAI-compatible format
    public static final String API_URL      = "http://localhost:11434/v1/chat/completions";
    public static final String MODEL        = "qwen2.5-coder:7b";
    public static final String ENV_KEY_NAME = "";       // no key needed for local Ollama
    public static final String API_VERSION  = "";

    public static final int MAX_TOKENS = 4096;
    public static final int TIMEOUT_S  = 300; // local inference can be slower — allow 5 min

    // -------------------------------------------------------------------------
    // Key resolution — no-op for Ollama
    // -------------------------------------------------------------------------

    public static String resolveApiKey() {
        return "ollama"; // Ollama accepts any non-empty string as a bearer token
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

    /**
     * Builds a plain-text prompt request using the OpenAI-compatible format
     * that Ollama exposes on /v1/chat/completions.
     *
     * Uses response_format = { type: "text" } for LLMTestOracle (which needs
     * a scratchpad section followed by JSON). The model is prompted to output
     * JSON at the end of its response; LLMTestOracle's parser finds the last
     * [ ... ] array, so the scratchpad preamble is safe.
     */
    public static String buildTextPayload(String prompt, ObjectMapper mapper) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", MODEL);
        body.put("max_tokens", MAX_TOKENS);
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "user", "content", prompt)));
        // Do NOT use json response_format here — LLMTestOracle expects scratchpad + JSON,
        // and strict JSON mode would reject the scratchpad preamble.
        return mapper.writeValueAsString(body);
    }

    /**
     * Builds a PDF-parsing request.
     *
     * Ollama does not support inline PDF blobs, so base64Pdf is ignored.
     * The extracted PDF text (from PDFBox) is embedded in the prompt instead.
     * Uses JSON response format since ExamPaperParser expects pure JSON output.
     */
    public static String buildPdfPayload(String prompt, String base64Pdf, String pdfText,
                                          ObjectMapper mapper) throws Exception {
        String combined = pdfText != null && !pdfText.isBlank()
                ? "=== EXAM PAPER TEXT ===\n" + pdfText + "\n\n=== TASK ===\n" + prompt
                : prompt;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", MODEL);
        body.put("max_tokens", MAX_TOKENS);
        body.put("temperature", 0.1);
        body.put("messages", List.of(
                Map.of("role", "user", "content", combined)));
        // JSON mode for exam paper parsing — we want clean JSON, no preamble
        body.put("response_format", Map.of("type", "json_object"));
        return mapper.writeValueAsString(body);
    }

    // -------------------------------------------------------------------------
    // Response extractor — OpenAI-compatible format
    // -------------------------------------------------------------------------

    /**
     * Extracts the text content from an Ollama /v1/chat/completions response.
     *
     * Response shape (OpenAI-compatible):
     * {
     *   "choices": [
     *     { "message": { "content": "..." } }
     *   ]
     * }
     */
    public static String extractText(String responseBody, ObjectMapper mapper) throws Exception {
        var root = mapper.readTree(responseBody);

        // Check for Ollama-level errors
        if (root.has("error")) {
            String errMsg = root.path("error").path("message").asText();
            if (errMsg.contains("model") && errMsg.contains("not found")) {
                throw new IllegalStateException(
                        "Ollama model not found: '" + MODEL + "'.\n"
                        + "Run: ollama pull " + MODEL);
            }
            throw new IllegalStateException("Ollama error: " + errMsg);
        }

        String text = root.path("choices").path(0)
                          .path("message").path("content").asText();

        if (text == null || text.isBlank()) {
            throw new IllegalStateException(
                    "Empty response from Ollama: "
                    + responseBody.substring(0, Math.min(300, responseBody.length())));
        }
        return text;
    }

    // -------------------------------------------------------------------------
    // HTTP header builder
    // -------------------------------------------------------------------------

    /**
     * Ollama's OpenAI-compatible endpoint accepts (but doesn't require) a
     * Bearer token. We send "ollama" as a placeholder so the Authorization
     * header is always present — some proxy setups require it.
     */
    public static HttpRequest.Builder addAuthHeaders(
            HttpRequest.Builder builder, String apiKey) {
        builder.header("Content-Type", "application/json");
        builder.header("Authorization", "Bearer ollama");
        return builder;
    }
}
