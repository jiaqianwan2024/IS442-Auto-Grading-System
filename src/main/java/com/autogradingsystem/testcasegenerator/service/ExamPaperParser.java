package com.autogradingsystem.testcasegenerator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * ExamPaperParser - Extracts per-question mark weights from an exam paper PDF.
 *
 * PACKAGE: com.autogradingsystem.testcasegenerator.service
 *
 * v3.5-ollama: Updated to use Ollama (qwen2.5-coder:7b) via LLMConfig.
 * Ollama does not accept native PDF/base64 input, so PDF text is always
 * extracted via PDFBox and embedded as plain text in the prompt.
 * The extractPdfText() method no longer short-circuits for any provider.
 *
 * INPUT:  resources/input/exam/*.pdf
 * OUTPUT: Map<String, Integer> e.g. { "Q1a" → 5, "Q1b" → 3, "Q3" → 10 }
 */
public class ExamPaperParser {

    public static final Path EXAM_DIR = Paths.get("resources/input/exam");

    private final HttpClient   http;
    private final ObjectMapper mapper;

    private Map<String, Integer> cachedWeights = null;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public ExamPaperParser() {
        this.http   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(LLMConfig.TIMEOUT_S))
                .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Legacy constructor — apiKey parameter kept for compile compatibility
     * but ignored (Ollama requires no key).
     */
    public ExamPaperParser(String apiKey) {
        this();
    }

    public static ExamPaperParser fromEnvironment() {
        return new ExamPaperParser();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public Map<String, Integer> extractMarkWeights() {
        if (cachedWeights != null) {
            System.out.println("  📄 Exam weights (cached): " + cachedWeights);
            return cachedWeights;
        }

        Path pdfPath = findExamPdf();
        if (pdfPath == null) {
            System.out.println("  ℹ️  No exam PDF in " + EXAM_DIR + " — falling back to CSV.");
            return Map.of();
        }

        System.out.println("  📄 Parsing exam paper: " + pdfPath.getFileName());

        try {
            Map<String, Integer> weights = callLLM(pdfPath);
            if (!weights.isEmpty()) {
                System.out.println("  ✅ Mark weights from exam PDF: " + weights);
                cachedWeights = weights;
            } else {
                System.err.println("  ⚠️  Exam PDF parsed but no weights found.");
            }
            return weights;
        } catch (Exception e) {
            System.err.println("  ⚠️  Exam PDF parse failed: " + e.getMessage());
            return Map.of();
        }
    }

    // -------------------------------------------------------------------------
    // HTTP call
    // -------------------------------------------------------------------------

    private Map<String, Integer> callLLM(Path pdfPath) throws Exception {
        String prompt = """
Read this exam paper carefully.
Extract EVERY question ID and its mark allocation.

Common patterns: "Question 1a (5 marks)", "Q1a [5]", "1a. ... [5 marks]", "(a) ... (5)"

Return ONLY a valid JSON object. No markdown, no explanation, no code fences.
Use IDs like "Q1a", "Q1b", "Q2", "Q3b" etc.

Example: {"Q1a": 5, "Q1b": 3, "Q2a": 8, "Q2b": 4, "Q3": 10}
""";

        // Ollama requires plain text — always extract PDF text via PDFBox
        String pdfText = extractPdfText(pdfPath);
        if (pdfText == null || pdfText.isBlank()) {
            System.err.println("  ⚠️  Could not extract text from PDF. "
                    + "Ensure PDFBox is on the classpath and the PDF is text-based (not scanned).");
            return Map.of();
        }

        String payload = LLMConfig.buildPdfPayload(prompt, "", pdfText, mapper);

        HttpRequest request = LLMConfig.addAuthHeaders(
                HttpRequest.newBuilder()
                        .uri(URI.create(LLMConfig.buildUrl("")))
                        .timeout(Duration.ofSeconds(LLMConfig.TIMEOUT_S)),
                "")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": "
                    + response.body().substring(0, Math.min(200, response.body().length())));
        }

        String text = LLMConfig.extractText(response.body(), mapper);
        return parseWeights(text);
    }

    // -------------------------------------------------------------------------
    // PDF text extraction via PDFBox (always used for Ollama)
    // -------------------------------------------------------------------------

    private String extractPdfText(Path pdfPath) {
        try {
            Class<?> loaderClass   = Class.forName("org.apache.pdfbox.Loader");
            Class<?> docClass      = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Class<?> stripperClass = Class.forName("org.apache.pdfbox.text.PDFTextStripper");

            // PDFBox 3.x: Loader.loadPDF(File)
            Object doc     = loaderClass.getMethod("loadPDF", java.io.File.class)
                                        .invoke(null, pdfPath.toFile());
            Object stripper = stripperClass.getDeclaredConstructor().newInstance();
            String text     = (String) stripperClass.getMethod("getText", docClass)
                                                     .invoke(stripper, doc);
            docClass.getMethod("close").invoke(doc);

            if (text == null || text.isBlank()) {
                System.err.println("  ⚠️  PDFBox extracted empty text from " + pdfPath.getFileName());
                System.err.println("      The PDF may be image-based (scanned). Try a text-based PDF.");
                return "";
            }

            System.out.println("  📄 PDF text extracted: " + text.length() + " chars");
            System.out.println("  📄 First 200 chars: "
                    + text.substring(0, Math.min(200, text.length())).replace("\n", " "));
            return text;

        } catch (ClassNotFoundException e) {
            System.err.println("  ⚠️  PDFBox not found on classpath. "
                    + "Add pdfbox-app to pom.xml dependencies.");
            return "";
        } catch (Exception e) {
            System.err.println("  ⚠️  PDF text extraction failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    private Map<String, Integer> parseWeights(String text) throws Exception {
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "")
                             .replaceAll("```\\s*$", "").trim();
        }
        Map<String, Object> raw = mapper.readValue(cleaned, new TypeReference<>() {});
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String qId = normaliseId(entry.getKey().trim());
            int marks;
            try { marks = ((Number) entry.getValue()).intValue(); }
            catch (Exception e) { marks = Integer.parseInt(entry.getValue().toString().trim()); }
            if (marks > 0) weights.put(qId, marks);
        }
        return weights;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Path findExamPdf() {
        if (!Files.exists(EXAM_DIR)) {
            try { Files.createDirectories(EXAM_DIR); } catch (Exception ignored) {}
            return null;
        }
        try (var stream = Files.list(EXAM_DIR)) {
            return stream.filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                         .findFirst().orElse(null);
        } catch (Exception e) { return null; }
    }

    private String normaliseId(String raw) {
        if (raw == null || raw.length() < 2) return raw;
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(raw.charAt(0)));
        for (int i = 1; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append(Character.isLetter(c) ? Character.toLowerCase(c) : c);
        }
        return sb.toString();
    }
}
