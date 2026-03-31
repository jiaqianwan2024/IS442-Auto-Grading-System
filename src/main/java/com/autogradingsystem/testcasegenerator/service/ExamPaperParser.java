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
 * Uses raw HTTP for all providers. For Cohere and other non-Anthropic providers,
 * PDF text is extracted via PDFBox and embedded in the prompt as plain text.
 * For Anthropic, the PDF is sent as a native base64 document block.
 *
 * INPUT:  resources/input/exam/*.pdf
 * OUTPUT: Map<String, Integer> e.g. { "Q1a" → 5, "Q1b" → 3, "Q3" → 10 }
 */
public class ExamPaperParser {

    public static final Path EXAM_DIR = Paths.get("resources/input/exam");

    private final String       apiKey;
    private final Path         examDir; 
    private final HttpClient   http;
    private final ObjectMapper mapper;

    private Map<String, Integer> cachedWeights      = null;
    private Map<String, String>  cachedDescriptions = null;
    private String               cachedPdfText      = null;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public ExamPaperParser(String apiKey) {
        this(apiKey, null);
    }

    public ExamPaperParser(String apiKey, Path examDir) {
        this.apiKey  = apiKey;
        this.examDir = examDir;
        this.http    = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(LLMConfig.TIMEOUT_S))
                .build();
        this.mapper  = new ObjectMapper();
    }

    public static ExamPaperParser fromEnvironment() {
        return new ExamPaperParser(LLMConfig.resolveApiKey(), null);
    }

    /** Path-aware factory — used by per-assessment flow */
    public static ExamPaperParser fromEnvironment(Path examDir) {
        return new ExamPaperParser(LLMConfig.resolveApiKey(), examDir);
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
            System.out.println("  ℹ️  No exam PDF in " + resolveExamDir() + " — falling back to CSV.");
            return Map.of();
        }

        System.out.println("  📄 Parsing exam paper: " + pdfPath.getFileName());

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("  ⚠️  No API key — cannot parse exam PDF.");
            return Map.of();
        }

        try {
            // Cache the PDF text so extractQuestionDescriptions() can reuse it
            // without reading the file a second time
            if (cachedPdfText == null) {
                cachedPdfText = extractPdfText(pdfPath);
            }

            Map<String, Integer> weights = callLLMForWeights(pdfPath, cachedPdfText);
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

    /**
     * Extracts per-question natural language requirements from the exam PDF.
     *
     * These are the specific logic rules the student must implement, e.g.:
     *   Q1a: "Words are separated by one or more spaces. A word is an isogram
     *         if it contains no repeated characters (case-insensitive)."
     *   Q1b: "Once a non-numeric character (excluding decimal point or e/E) is
     *         encountered, stop parsing."
     *
     * This is injected into QuestionSpec.description so the LLM input generator
     * knows what edge cases to create (e.g. "123.45.67", "this  is  spaced").
     *
     * Caches results — safe to call multiple times without extra LLM calls.
     */
    public Map<String, String> extractQuestionDescriptions() {
        if (cachedDescriptions != null) {
            System.out.println("  📄 Question descriptions (cached): "
                    + cachedDescriptions.keySet());
            return cachedDescriptions;
        }

        Path pdfPath = findExamPdf();
        if (pdfPath == null) {
            System.out.println("  ℹ️  No exam PDF — skipping description extraction.");
            return Map.of();
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("  ⚠️  No API key — cannot extract question descriptions.");
            return Map.of();
        }

        try {
            // Reuse cached PDF text if already extracted by extractMarkWeights()
            if (cachedPdfText == null) {
                cachedPdfText = extractPdfText(pdfPath);
            }
            if (cachedPdfText == null || cachedPdfText.isBlank()) {
                System.err.println("  ⚠️  PDF text empty — cannot extract descriptions.");
                return Map.of();
            }

            Map<String, String> descriptions = callLLMForDescriptions(cachedPdfText);
            if (!descriptions.isEmpty()) {
                System.out.println("  ✅ Question descriptions extracted: "
                        + descriptions.keySet());
                cachedDescriptions = descriptions;
            } else {
                System.err.println("  ⚠️  LLM returned no question descriptions.");
            }
            return descriptions;

        } catch (Exception e) {
            System.err.println("  ⚠️  Description extraction failed: " + e.getMessage());
            return Map.of();
        }
    }

    // -------------------------------------------------------------------------
    // HTTP call
    // -------------------------------------------------------------------------

    private Map<String, Integer> callLLMForWeights(Path pdfPath, String pdfText) throws Exception {
        String prompt = """
Read this exam paper carefully.
Extract EVERY question ID and its mark allocation.

Common patterns: "Question 1a (5 marks)", "Q1a [5]", "1a. ... [5 marks]", "(a) ... (5)"

Return ONLY a valid JSON object. No markdown, no explanation, no code fences.
Use IDs like "Q1a", "Q1b", "Q2", "Q3b" etc.

Example: {"Q1a": 5, "Q1b": 3, "Q2a": 8, "Q2b": 4, "Q3": 10}
""";

        byte[] pdfBytes  = Files.readAllBytes(pdfPath);
        String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);
        String payload   = LLMConfig.buildPdfPayload(prompt, base64Pdf, pdfText, mapper);

        HttpRequest request = LLMConfig.addAuthHeaders(
                HttpRequest.newBuilder()
                        .uri(URI.create(LLMConfig.buildUrl(apiKey)))
                        .timeout(Duration.ofSeconds(LLMConfig.TIMEOUT_S)),
                apiKey)
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

    /**
     * Identifies which question IDs are "script/classpath" questions — i.e. questions
     * that ask the student to write compile.sh / compile.bat / run.sh / run.bat and
     * arrange a folder structure, rather than implementing a Java method.
     *
     * These questions CANNOT be auto-generated by the LLM oracle because they have no
     * Java method to call. They must be graded by a hand-written tester (like Q4Tester.java)
     * that the instructor places in the testers folder directly.
     *
     * The generate-testers flow uses this to SKIP LLM generation for these questions.
     * If a matching *Tester.java already exists in the testers directory, it is kept
     * as-is. If it doesn't exist, a warning is printed and the question is skipped.
     *
     * Returns a Set of question IDs, e.g. {"Q4"}.
     * Returns empty set if no classpath questions are found or if PDF is unavailable.
     */
    public Set<String> extractScriptQuestions() {
        Path pdfPath = findExamPdf();
        if (pdfPath == null) return Set.of();
        if (apiKey == null || apiKey.isBlank()) return Set.of();

        try {
            if (cachedPdfText == null) cachedPdfText = extractPdfText(pdfPath);
            if (cachedPdfText == null || cachedPdfText.isBlank()) return Set.of();
            return callLLMForScriptQuestions(cachedPdfText);
        } catch (Exception e) {
            System.err.println("  ⚠️  Script question detection failed: " + e.getMessage());
            return Set.of();
        }
    }

    /**
     * Asks the LLM to identify classpath/script questions from the exam PDF text.
     *
     * Signal words we look for in the PDF:
     *   - "compile.sh", "compile.bat", "run.sh", "run.bat"
     *   - "classpath", "sourcepath", "folder structure", "Class Path"
     *   - Instructions to write shell/batch scripts
     *
     * Returns a JSON array of question IDs, e.g. ["Q4"].
     */
    private Set<String> callLLMForScriptQuestions(String pdfText) throws Exception {
        String prompt = """
Read this exam paper carefully.

Identify ALL question IDs where the student is asked to:
  - Write compile.sh, compile.bat, run.sh, or run.bat scripts
  - Set up a Java classpath or sourcepath
  - Arrange files into a specific folder structure and compile/run them via scripts
  - Any question involving shell scripts, batch files, or command-line Java compilation

These are "script questions" — the student's answer is a shell/batch script, NOT a Java method.

Return ONLY a valid JSON array of question IDs.
If there are no script questions, return an empty array: []

Examples: ["Q4"]  or  ["Q3", "Q4"]  or  []

No explanation, no markdown, just the JSON array.
""";

        String payload = LLMConfig.buildTextPayload(
                prompt + "\n\nEXAM PAPER TEXT:\n" + pdfText, mapper);

        HttpRequest request = LLMConfig.addAuthHeaders(
                HttpRequest.newBuilder()
                        .uri(URI.create(LLMConfig.buildUrl(apiKey)))
                        .timeout(Duration.ofSeconds(LLMConfig.TIMEOUT_S)),
                apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return Set.of();

        String text = LLMConfig.extractText(response.body(), mapper).trim();
        if (text.startsWith("```")) text = text.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```\\s*$", "").trim();

        // Parse JSON array
        java.util.List<Object> raw = mapper.readValue(text, new TypeReference<>() {});
        Set<String> result = new java.util.LinkedHashSet<>();
        for (Object item : raw) {
            String qId = normaliseId(item.toString().trim());
            if (!qId.isEmpty()) {
                result.add(qId);
                System.out.println("  📋 Script question detected: " + qId);
            }
        }
        return result;
    }

    /**
     * Asks the LLM to extract each question's specific functional requirements
     * as a JSON map.
     *
     * The prompt deliberately asks for:
     *   - Specific parsing rules (stop-conditions, delimiters)
     *   - Edge cases explicitly mentioned (e.g. "double dots", "multiple spaces")
     *   - Return-value contracts (what -1 means, what empty list means)
     *   - Exception conditions (what triggers DataException)
     *
     * This text is stored in QuestionSpec.description and injected into the
     * LLM input-generation prompt so it produces semantically correct inputs
     * instead of generic ones.
     */
    private Map<String, String> callLLMForDescriptions(String pdfText) throws Exception {
        String prompt = """
Read this exam paper carefully.

For each question, extract the SPECIFIC FUNCTIONAL REQUIREMENTS that a student's
implementation must satisfy. Focus especially on:
  - Parsing / stopping rules (e.g. "stop at second dot", "ignore non-integer objects")
  - Case sensitivity rules (e.g. "comparison is case-insensitive")
  - Edge case behaviour (e.g. "return -1.0 if file not found", "return empty list if no match")
  - Input format details (e.g. "words separated by one or more spaces")
  - Exception contracts (e.g. "throw DataException when course not found")
  - Return format (e.g. "Surname FIRSTNAME-gpa" format)
  - For script/classpath questions only, preserve any exact script constraints that appear in the paper

Return ONLY a valid JSON object. No markdown, no explanation, no code fences.
Keys must be question IDs like "Q1a", "Q1b", "Q2a".
Values must be 1-3 concise sentences describing the requirements.
If a question is a script/classpath question, include the key script constraints in the same concise style.

Example:
{
  "Q1a": "Return all isogram words (no repeated letters, case-insensitive) from the input list, preserving order.",
  "Q1b": "Sum only Integer elements from the mixed-type list. Ignore non-Integer objects (booleans, doubles, strings).",
  "Q2a": "Read persons from the given file (surname:first-age format). Average the ages for all persons matching the given surname (case-insensitive). Return -1.0 if the file cannot be found.",
  "Q2b": "Return the top student as SURNAME Firstname-gpa string. Throw DataException if the course is not found or if the file is missing.",
  "Q3": "Sort shapes by area descending, then perimeter descending. Exclude shapes where area exceeds 100 or radius > threshold."
}
""";

        String payload = LLMConfig.buildTextPayload(
                prompt + "\n\nEXAM PAPER TEXT:\n" + pdfText, mapper);

        HttpRequest request = LLMConfig.addAuthHeaders(
                HttpRequest.newBuilder()
                        .uri(URI.create(LLMConfig.buildUrl(apiKey)))
                        .timeout(Duration.ofSeconds(LLMConfig.TIMEOUT_S)),
                apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " for description extraction");
        }

        String text = LLMConfig.extractText(response.body(), mapper);
        return parseDescriptions(text);
    }

    /**
     * Parses the LLM JSON response into a Map<questionId, description>.
     * Normalises question IDs (Q1a, Q1b, etc.) for consistent lookup.
     */
    private Map<String, String> parseDescriptions(String text) throws Exception {
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "")
                             .replaceAll("```\\s*$", "").trim();
        }
        Map<String, Object> raw = mapper.readValue(cleaned, new TypeReference<>() {});
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String qId  = normaliseId(entry.getKey().trim());
            String desc = entry.getValue() == null ? "" : entry.getValue().toString().trim();
            desc = normalizeScriptDescription(desc);
            if (!desc.isEmpty()) descriptions.put(qId, desc);
        }
        return descriptions;
    }

    // -------------------------------------------------------------------------
    // PDF text extraction via PDFBox
    // -------------------------------------------------------------------------

    private String extractPdfText(Path pdfPath) {
        if ("anthropic".equals(LLMConfig.PROVIDER)) return ""; // not needed for Anthropic
        if ("gemini".equals(LLMConfig.PROVIDER)) return "";    // not needed for Gemini
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
            System.out.println("  📄 First 200 chars: " + text.substring(0, Math.min(200, text.length())).replace("\n", " "));
            return text;

        } catch (ClassNotFoundException e) {
            System.err.println("  ⚠️  PDFBox not found on classpath.");
            return "";
        } catch (Exception e) {
            System.err.println("  ⚠️  PDF text extraction failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            e.printStackTrace();
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

    private Path resolveExamDir() {
        return examDir != null ? examDir : EXAM_DIR;
    }

    private Path findExamPdf() {
        Path dir = resolveExamDir();
        if (!Files.exists(dir)) {
            try { Files.createDirectories(dir); } catch (Exception ignored) {}
            return null;
        }
        try (var stream = Files.list(dir)) {
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

    private String normalizeScriptDescription(String description) {
        if (description == null || description.isBlank()) return "";
        String normalized = description.replace("\r", " ").replace("\n", " ").replaceAll("\\s+", " ").trim();
        String lower = normalized.toLowerCase(Locale.ROOT);

        if (!looksLikeScriptDescription(lower)) {
            return normalized;
        }

        java.util.LinkedHashSet<String> parts = new java.util.LinkedHashSet<>();
        parts.add(normalized);

        if ((lower.contains("compile/run scripts") || lower.contains("compile and run scripts"))
                && (lower.contains("do not touch") || lower.contains("do not edit")
                || lower.contains("do not modify") || lower.contains("unchanged"))) {
            parts.add("Do not touch compile.sh/compile.bat or run.sh/run.bat.");
        }

        if ((lower.contains("either compile.sh") || lower.contains("either run.sh")
                || lower.contains("compile.sh/compile.bat") || lower.contains("run.sh/run.bat"))
                && (lower.contains("not both") || lower.contains("do not write in both")
                || lower.contains("do not write both") || lower.contains("either"))) {
            parts.add("Write only one of compile.sh/compile.bat and only one of run.sh/run.bat, not both.");
        }

        if (lower.contains("do not reference resource folder")
                || lower.contains("do not reference the resource folder")
                || lower.contains("must not reference resource")) {
            parts.add("Do not reference the resource folder in compile/run scripts.");
        }

        if (lower.contains("unnecessary folders/jars")
                || lower.contains("sourcepath/classpath")
                || lower.contains("do not include unnecessary")) {
            parts.add("Do not include unnecessary folders/jars in the sourcepath/classpath.");
        }

        if (lower.contains("application.java has main()")
                || lower.contains("application.java has the main()")
                || lower.contains("application.java has main method")) {
            parts.add("Application.java contains the main() method.");
        }

        if (lower.contains("compile to out folder") || lower.contains("compile to the out folder")
                || lower.contains("compiled to out folder")) {
            parts.add("Compile to the out folder.");
        }

        return String.join(" ", parts);
    }

    private boolean looksLikeScriptDescription(String lower) {
        if (lower == null || lower.isBlank()) return false;
        return lower.contains("compile.sh")
                || lower.contains("compile.bat")
                || lower.contains("run.sh")
                || lower.contains("run.bat")
                || lower.contains("classpath")
                || lower.contains("sourcepath")
                || lower.contains("compile/run scripts")
                || lower.contains("compile and run scripts")
                || lower.contains("compile to out folder")
                || lower.contains("expected stdout:");
    }
}
