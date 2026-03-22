package com.autogradingsystem.testcasegenerator.service;

import com.autogradingsystem.testcasegenerator.model.GeneratedTestCase;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec.MethodSpec;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec.ParamSpec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

/**
 * LLMTestOracle — generates test cases via the configured LLM provider.
 *
 * SPEED FIX: Collapsed from 2 API calls per method to 1.
 *   Previously: reasoning call (up to 120s) + JSON call (up to 120s) = up to 4 min per question.
 *   Now: single combined call with inline scratchpad → JSON output = ~15-30s per question.
 *   The prompt instructs the model to think first then immediately produce JSON in the same response.
 *
 * ACCURACY FIX: main() examples are extracted from the template source and
 *   injected verbatim into the prompt as ground-truth test cases. The LLM is told
 *   to copy these exactly rather than recompute expected values — eliminating the
 *   primary source of wrong expected values.
 */
public class LLMTestOracle {

    private final String       apiKey;
    private final HttpClient   http;
    private final ObjectMapper mapper;

    private final Map<String, List<GeneratedTestCase>> cache = new HashMap<>();

    private String examPdfText = null;

    private static final Set<String> SAFE_EQUALS_TYPES = Set.of(
            "String", "Integer", "Long", "Double", "Float",
            "Boolean", "Character", "int", "long", "double",
            "float", "boolean", "char", "Object"
    );

    public LLMTestOracle(String apiKey) {
        this.apiKey = apiKey;
        this.http   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(LLMConfig.TIMEOUT_S))
                .build();
        this.mapper = new ObjectMapper();
    }

    public static LLMTestOracle fromEnvironment() {
        return new LLMTestOracle(LLMConfig.resolveApiKey());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public List<GeneratedTestCase> generateTestCases(
            String questionId, QuestionSpec spec,
            List<MethodSpec> methods, int numTests) {

        Map<String, List<GeneratedTestCase>> perMethod = new LinkedHashMap<>();
        for (MethodSpec m : methods) {
            String cacheKey = questionId + "::" + m.getName() + "::" + numTests;
            perMethod.put(m.getName(),
                    cache.computeIfAbsent(cacheKey,
                            k -> callLLM(questionId, spec, m, numTests)));
        }

        List<GeneratedTestCase> result = new ArrayList<>();
        for (int i = 0; i < numTests; i++) {
            MethodSpec method = methods.get(i % methods.size());
            List<GeneratedTestCase> pool = perMethod.get(method.getName());
            int poolIndex = i / methods.size();
            if (poolIndex < pool.size()) {
                result.add(pool.get(poolIndex));
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Single HTTP call (replaces the old 2-call reasoning+JSON architecture)
    // -------------------------------------------------------------------------

    private List<GeneratedTestCase> callLLM(
            String questionId, QuestionSpec spec, MethodSpec method, int numTests) {

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("⚠️  No API key — cannot generate test cases for " + questionId);
            return new ArrayList<>();
        }

        try {
            String prompt   = buildCombinedPrompt(questionId, spec, method, numTests);
            String payload  = LLMConfig.buildTextPayload(prompt, mapper);

            HttpRequest req = LLMConfig.addAuthHeaders(
                    HttpRequest.newBuilder()
                            .uri(URI.create(LLMConfig.buildUrl(apiKey)))
                            .timeout(Duration.ofSeconds(LLMConfig.TIMEOUT_S)),
                    apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) {
                System.err.println("⚠️  LLMTestOracle: HTTP " + res.statusCode()
                        + " for " + questionId + "::" + method.getName());
                return new ArrayList<>();
            }

            String responseText = LLMConfig.extractText(res.body(), mapper);
            List<GeneratedTestCase> cases   = parseTestCases(responseText, method, numTests);
            List<GeneratedTestCase> repaired = validateAndRepair(cases, method);
            return sanitiseAll(repaired, method);

        } catch (Exception e) {
            System.err.println("⚠️  LLMTestOracle: failed for "
                    + questionId + "::" + method.getName() + " — " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // -------------------------------------------------------------------------
    // Combined prompt — think + JSON in one response
    // -------------------------------------------------------------------------

    private String buildCombinedPrompt(String questionId, QuestionSpec spec,
                                        MethodSpec method, int numTests) {

        String mainSource = spec.getSourceLines() != null && !spec.getSourceLines().isEmpty()
                ? String.join("\n", spec.getSourceLines()).replace("%", "%%")
                : "// No source available";

        // Extract main() examples verbatim — these are the ground-truth test cases
        List<MainExample> mainExamples = extractMainExamples(mainSource, method);

        StringBuilder supportingCtx = new StringBuilder();
        if (!spec.getSupportingSourceFiles().isEmpty()) {
            supportingCtx.append("=== SUPPORTING FILES ===\n");
            for (Map.Entry<String, String> e : spec.getSupportingSourceFiles().entrySet()) {
                supportingCtx.append("--- ").append(e.getKey()).append(" ---\n");
                supportingCtx.append(e.getValue().replace("%", "%%")).append("\n");
            }
        }

        String abstractWarning = buildAbstractClassWarning(spec);
        String methodSig       = buildMethodSignature(method);
        String returnType      = method.getReturnType();

        // Determine correct equality strategy for this return type
        String strategyGuide   = buildStrategyGuide(returnType, spec);

        StringBuilder p = new StringBuilder();

        // ── Instructions ──────────────────────────────────────────────────
        p.append("You are a Java test-case generator. Produce exactly ").append(numTests)
         .append(" test case(s) for the method below.\n\n");

        p.append("=== STRICT OUTPUT RULES ===\n");
        p.append("1. Your response must end with a JSON array [ {...}, ... ] and nothing after it.\n");
        p.append("2. Before the JSON, write a brief SCRATCHPAD section where you:\n");
        p.append("   a) Copy each example from main() — input and expected output EXACTLY as shown.\n");
        p.append("   b) If you need more test cases beyond what main() provides, trace the method\n");
        p.append("      line by line for your chosen input to determine the expected value.\n");
        p.append("   c) List your final ").append(numTests).append(" input→result pairs before writing JSON.\n");
        p.append("3. The JSON array is the ONLY thing the grading system reads. It must be valid JSON.\n\n");

        p.append("=== JAVA SYNTAX RULES — every value must be a valid standalone Java expression ===\n");
        p.append("  ArrayList:    new java.util.ArrayList<>(java.util.Arrays.asList(e1, e2))\n");
        p.append("  Empty list:   new java.util.ArrayList<>()\n");
        p.append("  String:       \\\"value\\\"   (escaped quotes inside JSON)\n");
        p.append("  int/long:     42  or  100L\n");
        p.append("  double/float: 3.14  or  3.14f\n");
        p.append("  boolean:      true  or  false\n");
        p.append("  Mixed list:   new java.util.ArrayList<>(java.util.Arrays.asList((Object)10, (Object)true, (Object)\\\"abc\\\"))\n");
        p.append("  ❌ NEVER: [1,2,3]  unquoted words  variable names  abstract class constructors\n\n");

        p.append("=== EQUALITY STRATEGY ===\n");
        p.append(strategyGuide).append("\n\n");

        if (!abstractWarning.isBlank()) {
            p.append(abstractWarning).append("\n");
        }

        // ── Method and source ──────────────────────────────────────────────
        p.append("=== METHOD TO TEST ===\n");
        p.append(methodSig).append("\n\n");

        p.append("=== FULL SOURCE (contains main() with ground-truth examples) ===\n");
        p.append(mainSource).append("\n\n");

        if (supportingCtx.length() > 0) {
            p.append(supportingCtx).append("\n");
        }

        // ── Extracted main() examples — most important section ─────────────
        if (!mainExamples.isEmpty()) {
            p.append("=== GROUND-TRUTH EXAMPLES FROM main() — USE THESE FIRST ===\n");
            p.append("These inputs and expected outputs are taken directly from the examiner's main() method.\n");
            p.append("Copy them EXACTLY into your test cases. Do NOT recompute expected values.\n\n");
            for (int i = 0; i < mainExamples.size(); i++) {
                MainExample ex = mainExamples.get(i);
                p.append("Example ").append(i + 1).append(":\n");
                p.append("  Input args:  ").append(ex.argsDescription).append("\n");
                p.append("  Expected:    ").append(ex.expectedDescription).append("\n\n");
            }
        }

        // ── JSON output format ─────────────────────────────────────────────
        p.append("=== OUTPUT FORMAT ===\n");
        p.append("Write SCRATCHPAD first, then end your response with this JSON array:\n");
        p.append("[\n");
        p.append("  {\n");
        p.append("    \"args\": [\"<arg1 as Java expression>\", \"<arg2>\"],\n");
        p.append("    \"expected\": \"<expected value as Java expression>\",\n");
        p.append("    \"equalityStrategy\": \"<strategy>\",\n");
        p.append("    \"rationale\": \"<one sentence explaining this test case>\"\n");
        p.append("  }\n");
        p.append("]\n\n");

        p.append("Produce exactly ").append(numTests).append(" test case(s). ");
        p.append("Prioritise the main() examples above. ");
        p.append("The JSON array must be the last thing in your response.\n");

        return p.toString();
    }

    // -------------------------------------------------------------------------
    // Extract main() examples from source
    // Each example is a structured input→expected pair parsed from the main() body
    // -------------------------------------------------------------------------

    private static class MainExample {
        String argsDescription;    // human-readable for the scratchpad
        String expectedDescription; // human-readable for the scratchpad
    }

    /**
     * Parses the main() method body to extract input→expected pairs.
     * These are injected verbatim into the prompt so the LLM copies rather than recomputes.
     * Handles the common IS442 pattern:
     *   - ArrayList<> inputs built with .add() calls
     *   - expected = literal value
     *   - String filename + String surname/courseName args
     *   - Shape list args
     */
    private List<MainExample> extractMainExamples(String source, MethodSpec method) {
        List<MainExample> examples = new ArrayList<>();
        try {
            // Find main() body
            int mainIdx = source.indexOf("public static void main(");
            if (mainIdx < 0) return examples;
            int braceStart = source.indexOf('{', mainIdx);
            if (braceStart < 0) return examples;

            // Find all { } blocks inside main — each is a test case block
            int depth = 1, i = braceStart + 1;
            int blockStart = -1;
            List<String> blocks = new ArrayList<>();

            while (i < source.length() && depth > 0) {
                char c = source.charAt(i);
                if (c == '{') {
                    depth++;
                    if (depth == 2) blockStart = i;
                } else if (c == '}') {
                    depth--;
                    if (depth == 1 && blockStart >= 0) {
                        blocks.add(source.substring(blockStart + 1, i));
                        blockStart = -1;
                    }
                }
                i++;
            }

            for (String block : blocks) {
                MainExample ex = parseBlock(block, method);
                if (ex != null) examples.add(ex);
            }
        } catch (Exception e) {
            // ignore — examples are optional
        }
        return examples;
    }

    private MainExample parseBlock(String block, MethodSpec method) {
        MainExample ex = new MainExample();

        // Extract expected value — look for: type expected = <value>;
        Pattern expectedPat = Pattern.compile(
                "(?:int|double|float|long|boolean|String)\\s+expected\\s*=\\s*([^;]+);");
        Matcher em = expectedPat.matcher(block);
        if (!em.find()) return null;
        String expectedRaw = em.group(1).trim();

        // Build args description based on method parameter types
        List<ParamSpec> params = method.getParams();
        StringBuilder argsDesc = new StringBuilder();

        if (params.size() == 1 && isListType(params.get(0).getType())) {
            // ArrayList-based question (Q1a, Q1b, Q3)
            argsDesc.append(extractListContents(block, params.get(0).getType()));
        } else if (params.size() == 2
                && "String".equals(params.get(0).getType())
                && "String".equals(params.get(1).getType())) {
            // File-based question (Q2a, Q2b) — extract string literals
            List<String> strArgs = extractStringArgs(block, method.getName());
            if (strArgs.size() >= 2) {
                argsDesc.append("\"").append(strArgs.get(0)).append("\", \"")
                        .append(strArgs.get(1)).append("\"");
            }
        } else {
            argsDesc.append("(see main() source above)");
        }

        ex.argsDescription    = argsDesc.toString();
        ex.expectedDescription = expectedRaw;
        return ex;
    }

    private String extractListContents(String block, String paramType) {
        // Look for .add(...) calls and collect them
        Pattern addPat = Pattern.compile("\\.add\\(([^)]+)\\)");
        Matcher m = addPat.matcher(block);
        List<String> items = new ArrayList<>();
        while (m.find()) {
            items.add(m.group(1).trim());
        }
        if (items.isEmpty()) return "new java.util.ArrayList<>()";

        String elemType = extractGenericType(paramType);
        StringBuilder sb = new StringBuilder("new java.util.ArrayList<>(java.util.Arrays.asList(");

        // For Object lists, cast each item
        boolean isObjectList = "Object".equals(elemType);

        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            if (isObjectList) {
                // Determine cast type
                if (item.startsWith("\"")) sb.append("(Object)").append(item);
                else if (item.equals("true") || item.equals("false")) sb.append("(Object)").append(item);
                else if (item.contains(".") && !item.startsWith("\"")) sb.append("(Object)").append(item);
                else sb.append("(Object)").append(item);
            } else {
                sb.append(item);
            }
            if (i < items.size() - 1) sb.append(", ");
        }
        sb.append("))");
        return sb.toString();
    }

    private List<String> extractStringArgs(String block, String methodName) {
        // Find the method call line: methodName("arg1", "arg2")
        Pattern callPat = Pattern.compile(
                methodName + "\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*\\)");
        Matcher m = callPat.matcher(block);
        if (m.find()) {
            return List.of(m.group(1), m.group(2));
        }
        // Fallback: find printf line which shows the args
        Pattern printfPat = Pattern.compile("printf[^,]+,\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"");
        Matcher m2 = printfPat.matcher(block);
        if (m2.find()) {
            return List.of(m2.group(1), m2.group(2));
        }
        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Strategy guide — tailored per return type
    // -------------------------------------------------------------------------

    private String buildStrategyGuide(String returnType, QuestionSpec spec) {
        if (isListType(returnType)) {
            String elemType = extractGenericType(returnType);
            if (SAFE_EQUALS_TYPES.contains(elemType)) {
                return "Use \"list_equals\" — return type is List<" + elemType + ">.\n"
                     + "  expected must be: new java.util.ArrayList<>(java.util.Arrays.asList(...))";
            } else {
                return "Use \"toString_equals\" — return type is List<" + elemType + "> (custom object).\n"
                     + "  expected must be a QUOTED STRING matching the exact .toString() output.\n"
                     + "  Example: \"[R3=>30.0:22.0, R1=>20.0:24.0]\"  (copy from main() exactly)";
            }
        }
        if ("String".equals(returnType)) {
            return "Use \"equals\" — return type is String.\n"
                 + "  expected must be a quoted string: \\\"John LEE-4.0\\\"";
        }
        if ("double".equals(returnType) || "float".equals(returnType)) {
            return "Use \"eq_float\" — return type is " + returnType + " (tolerance ±1e-9).\n"
                 + "  expected must be a numeric literal: 36.0";
        }
        if ("int".equals(returnType) || "long".equals(returnType) || "boolean".equals(returnType)) {
            return "Use \"==\" — return type is " + returnType + " (primitive).\n"
                 + "  expected must be a numeric or boolean literal: 60";
        }
        return "Use \"equals\" for object types, \"==\" for primitives, \"eq_float\" for double/float.";
    }

    // -------------------------------------------------------------------------
    // Abstract class warning
    // -------------------------------------------------------------------------

    private String buildAbstractClassWarning(QuestionSpec spec) {
        List<String> abstractClasses = new ArrayList<>();
        for (Map.Entry<String, String> entry : spec.getSupportingSourceFiles().entrySet()) {
            String source = entry.getValue();
            Pattern p = Pattern.compile("(?:abstract class|interface)\\s+(\\w+)");
            Matcher m = p.matcher(source);
            while (m.find()) abstractClasses.add(m.group(1));
        }
        if (spec.getSourceLines() != null) {
            String mainSource = String.join("\n", spec.getSourceLines());
            Pattern p = Pattern.compile("(?:abstract class|interface)\\s+(\\w+)");
            Matcher m = p.matcher(mainSource);
            while (m.find()) abstractClasses.add(m.group(1));
        }
        if (abstractClasses.isEmpty()) return "";
        return "=== ABSTRACT/INTERFACE — NEVER instantiate these with 'new': "
                + String.join(", ", abstractClasses) + " ===\n"
                + "Use concrete subclasses from supporting files instead (e.g. Rectangle, Circle).";
    }

    private String buildMethodSignature(MethodSpec method) {
        StringBuilder sb = new StringBuilder("public ");
        if (method.isStatic()) sb.append("static ");
        sb.append(method.getReturnType()).append(" ").append(method.getName()).append("(");
        List<ParamSpec> params = method.getParams();
        for (int i = 0; i < params.size(); i++) {
            sb.append(params.get(i).getType()).append(" ").append(params.get(i).getName());
            if (i < params.size() - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Post-processing sanitiser (unchanged)
    // -------------------------------------------------------------------------

    private List<GeneratedTestCase> sanitiseAll(List<GeneratedTestCase> cases, MethodSpec method) {
        List<GeneratedTestCase> result = new ArrayList<>();
        for (GeneratedTestCase tc : cases) {
            GeneratedTestCase sanitised = sanitise(tc, method);
            if (sanitised != null) result.add(sanitised);
        }
        return result;
    }

    private GeneratedTestCase sanitise(GeneratedTestCase tc, MethodSpec method) {
        if (tc.isSmokeTest() || tc.isVoidCheck()) return tc;

        String expected   = tc.getExpected();
        String strategy   = tc.getEqualityStrategy();
        String returnType = method.getReturnType();
        List<String> args = tc.getArgs() != null ? new ArrayList<>(tc.getArgs()) : new ArrayList<>();

        boolean isCustomList = isListType(returnType)
                && !SAFE_EQUALS_TYPES.contains(extractGenericType(returnType));
        if (isCustomList) {
            strategy = "toString_equals";
            expected = repairToStringExpected(expected);
            if (SMOKE_TEST_MARKER.equals(expected)) return null;
        } else if ("toString_equals".equals(strategy)) {
            expected = repairToStringExpected(expected);
            if (SMOKE_TEST_MARKER.equals(expected)) return null;
        } else {
            expected = repairExpected(expected, returnType);
        }

        List<ParamSpec> params = method.getParams();
        for (int i = 0; i < args.size() && i < params.size(); i++) {
            args.set(i, repairArg(args.get(i), params.get(i).getType()));
        }

        return new GeneratedTestCase(args, expected,
                tc.isVoidCheck(), tc.isSmokeTest(),
                tc.getRationale(), strategy);
    }

    private static final String SMOKE_TEST_MARKER = "__SMOKE_TEST__";

    private String repairToStringExpected(String raw) {
        if (raw == null || raw.isBlank()) return SMOKE_TEST_MARKER;
        String t = raw.trim();
        if (t.startsWith("\"")) return t;
        if (t.startsWith("[")) return "\"" + t.replace("\"", "\\\"") + "\"";
        if (t.startsWith("new ") || t.startsWith("java.")) {
            System.err.println("⚠️  toString_equals: cannot use Java expression as expected — smoke test: "
                    + t.substring(0, Math.min(60, t.length())));
            return SMOKE_TEST_MARKER;
        }
        return "\"" + t.replace("\"", "\\\"") + "\"";
    }

    private String repairExpected(String raw, String returnType) {
        if (raw == null || raw.isBlank()) return safeDefault(returnType, 0);
        String trimmed = raw.trim();
        if (trimmed.startsWith("new ") || trimmed.startsWith("\"")
                || trimmed.startsWith("java.") || trimmed.matches("-?\\d.*")
                || trimmed.equals("true") || trimmed.equals("false")
                || trimmed.equals("null") || trimmed.startsWith("'")) {
            return trimmed;
        }
        if (trimmed.equals("[]")) return isListType(returnType) ? "new java.util.ArrayList<>()" : trimmed;
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) return repairPythonList(trimmed, returnType);
        if (isStringType(returnType) && !trimmed.contains("(") && !trimmed.startsWith("\"")) {
            return "\"" + trimmed.replace("\"", "\\\"") + "\"";
        }
        return trimmed;
    }

    private String repairPythonList(String bracketExpr, String returnType) {
        String inner = bracketExpr.substring(1, bracketExpr.length() - 1).trim();
        if (inner.isEmpty()) return "new java.util.ArrayList<>()";
        String[] items = inner.split(",(?![^<>]*>)"); // split on comma not inside generics
        String elementType = extractGenericType(returnType);
        StringBuilder sb = new StringBuilder("new java.util.ArrayList<>(java.util.Arrays.asList(");
        for (int i = 0; i < items.length; i++) {
            String item = items[i].trim();
            if (("String".equals(elementType) || "Object".equals(elementType))
                    && !item.startsWith("\"") && !item.equals("null")) {
                sb.append("\"").append(item.replace("\"", "\\\"")).append("\"");
            } else {
                sb.append(item);
            }
            if (i < items.length - 1) sb.append(", ");
        }
        sb.append("))");
        return sb.toString();
    }

    private String repairArg(String raw, String paramType) {
        if (raw == null) return safeDefault(paramType, 0);
        String repaired = repairExpected(raw, paramType);
        repaired = repaired.replaceAll("\\(Object\\)(-\\d)", "(Object)($1)");
        return repaired;
    }

    // -------------------------------------------------------------------------
    // Response parsing — extracts the JSON array from end of response
    // -------------------------------------------------------------------------

    private List<GeneratedTestCase> parseTestCases(
            String llmText, MethodSpec method, int numTests) {

        String cleaned = llmText.trim();

        // Strip markdown fences
        if (cleaned.contains("```")) {
            cleaned = cleaned.replaceAll("```[a-zA-Z]*\\n?", "").trim();
        }

        // Find the LAST [ ... ] array in the response (after the scratchpad)
        int arrayStart = cleaned.lastIndexOf('[');
        int arrayEnd   = cleaned.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            cleaned = cleaned.substring(arrayStart, arrayEnd + 1);
        }

        cleaned = quoteUnquotedJavaExpressions(cleaned);

        List<GeneratedTestCase> result = new ArrayList<>();
        try {
            List<Map<String, Object>> raw = mapper.readValue(cleaned, new TypeReference<>() {});
            for (Map<String, Object> entry : raw) {
                try {
                    List<String> args = castToStringList(entry.get("args"));
                    String expected   = entry.get("expected") == null
                                        ? "null" : entry.get("expected").toString();
                    String strategy   = entry.getOrDefault("equalityStrategy", "equals").toString();
                    String rationale  = entry.getOrDefault("rationale", "").toString();
                    result.add(new GeneratedTestCase(
                            args, expected, method.returnsVoid(), false, rationale, strategy));
                } catch (Exception ex) {
                    System.err.println("⚠️  Skipped one test case for " + method.getName());
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️  Could not parse LLM response for "
                    + method.getName() + ": " + e.getMessage());
            return new ArrayList<>();
        }
        return result;
    }

    private String quoteUnquotedJavaExpressions(String json) {
        String[] lines = json.split("\n");
        StringBuilder out = new StringBuilder();
        for (String line : lines) out.append(fixLine(line)).append("\n");
        return out.toString();
    }

    private String fixLine(String line) {
        String trimmed = line.trim();
        if (trimmed.matches("\"\\w+\"\\s*:\\s*new .*")) {
            int colonIdx = line.indexOf(':');
            String key = line.substring(0, colonIdx + 1);
            String val = line.substring(colonIdx + 1).trim();
            boolean hadComma = val.endsWith(",");
            if (hadComma) val = val.substring(0, val.length() - 1).trim();
            return key + " \"" + val.replace("\"", "\\\"") + "\"" + (hadComma ? "," : "");
        }
        if (trimmed.startsWith("[new ")) {
            int bracketIdx = line.indexOf('[');
            String before = line.substring(0, bracketIdx + 1);
            String rest = line.substring(bracketIdx + 1).trim();
            String suffix = "";
            if (rest.endsWith("],"))      { rest = rest.substring(0, rest.length() - 2).trim(); suffix = "],"; }
            else if (rest.endsWith("]")) { rest = rest.substring(0, rest.length() - 1).trim(); suffix = "]";  }
            return before + "\"" + rest.replace("\"", "\\\"") + "\"" + suffix;
        }
        return line;
    }

    // -------------------------------------------------------------------------
    // Null-arg repair
    // -------------------------------------------------------------------------

    private List<GeneratedTestCase> validateAndRepair(
            List<GeneratedTestCase> cases, MethodSpec method) {

        List<ParamSpec> params = method.getParams();
        List<GeneratedTestCase> repaired = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            GeneratedTestCase tc = cases.get(i);
            if (tc.isSmokeTest() || tc.isVoidCheck() || tc.getArgs() == null) {
                repaired.add(tc); continue;
            }
            List<String> fixedArgs = new ArrayList<>(tc.getArgs());
            boolean changed = false;
            for (int j = 0; j < params.size() && j < fixedArgs.size(); j++) {
                if ("null".equals(fixedArgs.get(j))) {
                    fixedArgs.set(j, safeDefault(params.get(j).getType(), i));
                    changed = true;
                }
            }
            repaired.add(changed
                    ? new GeneratedTestCase(fixedArgs, tc.getExpected(), tc.isVoidCheck(),
                            tc.isSmokeTest(), tc.getRationale(), tc.getEqualityStrategy())
                    : tc);
        }
        return repaired;
    }

    // -------------------------------------------------------------------------
    // Fallbacks
    // -------------------------------------------------------------------------

    private GeneratedTestCase smokeTestFallback(MethodSpec method, int index) {
        List<String> args = new ArrayList<>();
        for (ParamSpec p : method.getParams()) args.add(safeDefault(p.getType(), index));
        return GeneratedTestCase.smokeTest(args, "Smoke test fallback");
    }

    // -------------------------------------------------------------------------
    // Type utilities
    // -------------------------------------------------------------------------

    private boolean isListType(String type) {
        if (type == null) return false;
        String t = type.trim();
        return t.startsWith("List") || t.startsWith("ArrayList")
                || t.startsWith("java.util.List") || t.startsWith("java.util.ArrayList");
    }

    private boolean isStringType(String type) {
        if (type == null) return false;
        return "String".equals(type.trim()) || "java.lang.String".equals(type.trim());
    }

    private String extractGenericType(String type) {
        if (type == null) return "Object";
        int lt = type.indexOf('<'), gt = type.lastIndexOf('>');
        if (lt >= 0 && gt > lt) return type.substring(lt + 1, gt).trim();
        return "Object";
    }

    private String safeDefault(String type, int seed) {
        if (type == null) return "null";
        String t = type.trim();
        if (isListType(t)) {
            String elem = extractGenericType(t);
            if ("String".equals(elem))
                return "new java.util.ArrayList<>(java.util.Arrays.asList(\"hello\", \"world\"))";
            if ("Integer".equals(elem) || "int".equals(elem))
                return "new java.util.ArrayList<>(java.util.Arrays.asList(1, 2, 3))";
            if ("Object".equals(elem))
                return "new java.util.ArrayList<>(java.util.Arrays.asList((Object)10, (Object)20, (Object)30))";
            return "new java.util.ArrayList<>()";
        }
        if (t.contains("Map"))    return "new java.util.HashMap<>()";
        if (t.contains("Set"))    return "new java.util.HashSet<>()";
        if (t.equals("int[]"))    return "new int[]{1, 2, 3}";
        if (t.equals("double[]")) return "new double[]{1.0, 2.0, 3.0}";
        if (t.equals("String[]")) return "new String[]{\"a\", \"b\", \"c\"}";
        return switch (t) {
            case "int", "Integer"     -> String.valueOf(seed % 10 + 1);
            case "long", "Long"       -> seed + "L";
            case "double", "Double"   -> seed + ".0";
            case "float", "Float"     -> seed + ".0f";
            case "boolean", "Boolean" -> seed % 2 == 0 ? "true" : "false";
            case "char", "Character"  -> "'a'";
            case "String"             -> "\"hello\"";
            default                   -> "null";
        };
    }

    @SuppressWarnings("unchecked")
    private List<String> castToStringList(Object obj) {
        if (obj instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) result.add(item == null ? "null" : item.toString());
            return result;
        }
        return List.of(obj == null ? "null" : obj.toString());
    }

    private String getExamPdfText() {
        if (examPdfText != null) return examPdfText;
        try {
            if (!Files.exists(ExamPaperParser.EXAM_DIR)) { examPdfText = ""; return examPdfText; }
            try (var stream = Files.list(ExamPaperParser.EXAM_DIR)) {
                Optional<Path> pdf = stream
                        .filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                        .findFirst();
                if (pdf.isPresent()) {
                    examPdfText = extractPdfTextViaPdfBox(pdf.get());
                    if (examPdfText == null) examPdfText = "";
                } else { examPdfText = ""; }
            }
        } catch (Exception e) { examPdfText = ""; }
        return examPdfText;
    }

    private String extractPdfTextViaPdfBox(Path pdfPath) {
        try {
            Class<?> loaderClass   = Class.forName("org.apache.pdfbox.Loader");
            Class<?> docClass      = Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
            Class<?> stripperClass = Class.forName("org.apache.pdfbox.text.PDFTextStripper");
            Object doc      = loaderClass.getMethod("loadPDF", java.io.File.class)
                                         .invoke(null, pdfPath.toFile());
            Object stripper = stripperClass.getDeclaredConstructor().newInstance();
            String text     = (String) stripperClass.getMethod("getText", docClass)
                                                     .invoke(stripper, doc);
            docClass.getMethod("close").invoke(doc);
            return text != null && text.length() > 8000 ? text.substring(0, 8000) : text;
        } catch (Exception e) { return null; }
    }
}