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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.*;

public class LLMTestOracle {

    private final String apiKey;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, List<GeneratedTestCase>> cache = new ConcurrentHashMap<>();

    private static final Set<String> SAFE_EQUALS_TYPES = Set.of(
            "String","Integer","Long","Double","Float","Boolean","Character",
            "int","long","double","float","boolean","char","Object");

    public LLMTestOracle(String apiKey) {
        this.apiKey = apiKey;
        this.http   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(LLMConfig.TIMEOUT_S)).build();
        this.mapper = new ObjectMapper();
    }

    public static LLMTestOracle fromEnvironment() {
        return new LLMTestOracle(LLMConfig.resolveApiKey());
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public List<GeneratedTestCase> generateTestCases(
            String questionId, QuestionSpec spec, List<MethodSpec> methods, int numTests) {

        Map<String, List<GeneratedTestCase>> perMethod = new LinkedHashMap<>();
        for (MethodSpec m : methods) {
            String key = questionId + "::" + m.getName() + "::" + numTests;
            perMethod.put(m.getName(),
                    cache.computeIfAbsent(key, k -> generateForMethod(questionId, spec, m, numTests)));
        }

        List<GeneratedTestCase> result = new ArrayList<>();
        for (int i = 0; i < numTests; i++) {
            MethodSpec method = methods.get(i % methods.size());
            List<GeneratedTestCase> pool = perMethod.get(method.getName());
            int idx = i / methods.size();
            if (idx < pool.size()) result.add(pool.get(idx));
        }
        return result;
    }

    // =========================================================================
    // Orchestration
    // =========================================================================

    private List<GeneratedTestCase> generateForMethod(
            String questionId, QuestionSpec spec, MethodSpec method, int numTests) {

        String mainSource = spec.getSourceLines() != null && !spec.getSourceLines().isEmpty()
                ? String.join("\n", spec.getSourceLines()) : "";

        // FIX 2+3: improved parser handles String expected="..." and DataException
        List<GeneratedTestCase> mainCases = extractMainExampleCases(mainSource, method);

        if (mainCases.size() >= numTests) {
            System.out.println("   OK Using " + numTests + " main() examples for "
                    + questionId + "::" + method.getName());
            return mainCases.subList(0, numTests);
        }

        int needed = numTests - mainCases.size();
        List<List<String>> llmInputs = callLLMForInputsOnly(questionId, spec, method, needed);

        if (llmInputs.isEmpty()) return padWithSmoke(mainCases, numTests, method);

        List<GeneratedTestCase> oracleCases =
                deriveExpectedFromTemplate(questionId, spec, method, llmInputs);

        List<GeneratedTestCase> combined = new ArrayList<>(mainCases);
        combined.addAll(oracleCases);
        return combined.size() >= numTests
                ? combined.subList(0, numTests)
                : padWithSmoke(combined, numTests, method);
    }

    // =========================================================================
    // FIX 2+3: Improved main() parser
    // =========================================================================

    private List<GeneratedTestCase> extractMainExampleCases(String source, MethodSpec method) {
        List<GeneratedTestCase> cases = new ArrayList<>();
        try {
            int mainIdx = source.indexOf("public static void main(");
            if (mainIdx < 0) return cases;
            int braceStart = source.indexOf('{', mainIdx);
            if (braceStart < 0) return cases;

            int depth = 1, i = braceStart + 1, blockStart = -1;
            List<String> blocks = new ArrayList<>();
            while (i < source.length() && depth > 0) {
                char c = source.charAt(i);
                if (c == '{') { depth++; if (depth == 2) blockStart = i; }
                else if (c == '}') {
                    depth--;
                    if (depth == 1 && blockStart >= 0) {
                        blocks.add(source.substring(blockStart + 1, i));
                        blockStart = -1;
                    }
                }
                i++;
            }

            for (String block : blocks) {
                if (block.contains("DataException")) {
                    GeneratedTestCase tc = parseExceptionBlock(block, method);
                    if (tc != null) cases.add(tc);
                } else {
                    GeneratedTestCase tc = parseMainBlock(block, method);
                    if (tc != null) cases.add(tc);
                }
            }
        } catch (Exception ignored) {}
        return cases;
    }

    // FIX 3: exception test cases
    private GeneratedTestCase parseExceptionBlock(String block, MethodSpec method) {
        List<String> args = buildArgsFromMainBlock(block, method);
        if (args.isEmpty()) return null;
        return new GeneratedTestCase(args, "DataException", false, false,
                "Expects DataException", "exception");
    }

    // FIX 2: broadened to match String expected = "..."
    private GeneratedTestCase parseMainBlock(String block, MethodSpec method) {
        // FIX: Added List<[^>]+> and ArrayList<[^>]+> to the regex match
        Pattern pat = Pattern.compile(
                "(?:int|double|float|long|boolean|String|List<[^>]+>|ArrayList<[^>]+>)\\s+expected\\s*=\\s*([^;]+);");
        Matcher em = pat.matcher(block);
        if (!em.find()) return null;
        String expectedRaw = em.group(1).trim();

        String retType = method.getReturnType();
        String strategy = resolveStrategyForType(retType);
        String expected;

        if (isListType(retType)) {
            // String expected = "[R2=>10.0:13.0]"  -- already the toString form
            expected = expectedRaw.startsWith("\"") ? expectedRaw
                    : "\"" + expectedRaw.replace("\"", "\\\"") + "\"";
            strategy = "toString_equals";
        } else if ("String".equals(retType)) {
            expected = expectedRaw.startsWith("\"") ? expectedRaw : "\"" + expectedRaw + "\"";
        } else {
            expected = expectedRaw;
        }

        List<String> args = buildArgsFromMainBlock(block, method);
        if (args.isEmpty()) return null;
        return new GeneratedTestCase(args, expected, false, false, "From main()", strategy);
    }

    private List<String> buildArgsFromMainBlock(String block, MethodSpec method) {
        List<ParamSpec> params = method.getParams();
        List<String> args = new ArrayList<>();

        if (params.size() == 1 && isListType(params.get(0).getType())) {
            // FIX: Use balanced parenthesis matching instead of regex to support nested objects
            List<String> items = new ArrayList<>();
            int addIdx = 0;
            while ((addIdx = block.indexOf(".add(", addIdx)) >= 0) {
                int start = addIdx + 5;
                int depth = 1;
                int end = start;
                while (end < block.length() && depth > 0) {
                    char c = block.charAt(end);
                    if (c == '(') depth++;
                    else if (c == ')') depth--;
                    end++;
                }
                if (depth == 0) {
                    items.add(block.substring(start, end - 1).trim());
                }
                addIdx = end;
            }
            if (!items.isEmpty()) {
                args.add("new java.util.ArrayList<>(java.util.Arrays.asList("
                        + String.join(", ", items) + "))");
            }
        } else if (params.size() == 2
                && "String".equals(params.get(0).getType())
                && "String".equals(params.get(1).getType())) {
            Pattern callPat = Pattern.compile(method.getName() + "\\s*\\(([^)]*)\\)");
            String[] argExprs = null;
            for (String line : block.split("\\n")) {
                String trimmed = line.trim();
                if (trimmed.contains("printf") || trimmed.contains("println") || trimmed.contains("print(")) {
                    continue;
                }
                Matcher callM = callPat.matcher(trimmed);
                if (callM.find()) {
                    List<String> raw = splitCallArgs(callM.group(1));
                    if (raw.size() >= 2) {
                        argExprs = new String[] { raw.get(0).trim(), raw.get(1).trim() };
                        break;
                    }
                }
            }
            if (argExprs != null) {
                args.add(resolveStringArgument(block, argExprs[0]));
                args.add(resolveStringArgument(block, argExprs[1]));
            }
        } else if (params.size() == 1 && "String".equals(params.get(0).getType())) {
            // ── FIX: Single-String parameter (e.g. reorderWordsInSentence, stringToDouble) ──
            // The template stores the input in a variable: String input = "...";
            // then calls: method(input)
            // The old fallback regex captured the variable NAME "input" — not its value.
            // We now resolve it: look for `String <varName> = "<value>";` in the block,
            // then verify that <varName> is what's passed to the method call.
            //
            // CRITICAL: We must skip printf/println lines because they contain
            // format strings like reorderWordsInSentence("%s") which would be matched
            // before the real method call line (e.g. String actual = method(input)).

            // Step 1: find the method call, skipping printf/println lines
            Pattern callPat = Pattern.compile(method.getName() + "\\s*\\(([^)]+)\\)");
            String argExpr = null;
            for (String line : block.split("\n")) {
                String trimmed = line.trim();
                // Skip print/printf/println lines — contain format strings, not real args
                if (trimmed.contains("printf") || trimmed.contains("println")
                        || trimmed.contains("print(")) {
                    continue;
                }
                Matcher callM = callPat.matcher(trimmed);
                if (callM.find()) {
                    argExpr = callM.group(1).trim();
                    break;
                }
            }

            if (argExpr != null && argExpr.startsWith("\"")) {
                // Already an inline literal — use directly
                args.add(argExpr);
            } else if (argExpr != null) {
                // It's a variable name — look up its assignment in the block
                // Pattern: String <varName> = "<value>";  (value may be empty string)
                Pattern varPat = Pattern.compile(
                        "String\\s+" + Pattern.quote(argExpr)
                        + "\\s*=\\s*\"([^\"]*)\";");
                Matcher varM = varPat.matcher(block);
                if (varM.find()) {
                    // Resolved the variable to its string literal value
                    args.add("\"" + varM.group(1) + "\"");
                } else {
                    // Variable assignment not found — treat as literal (best effort)
                    args.add("\"" + argExpr + "\"");
                }
            }
        } else {
            // Generic fallback: try to extract inline literals or variable references
            Pattern callPat = Pattern.compile(method.getName() + "\\s*\\(([^)]+)\\)");
            Matcher m = callPat.matcher(block);
            if (m.find()) {
                for (String a : m.group(1).split(",")) args.add(a.trim());
            }
        }
        return args;
    }

    // =========================================================================
    // Phase 1: LLM inputs-only (FIX 1: injects data file contents)
    // =========================================================================

    private List<List<String>> callLLMForInputsOnly(
            String questionId, QuestionSpec spec, MethodSpec method, int numTests) {
        if (apiKey == null || apiKey.isBlank()) return Collections.emptyList();
        try {
            String prompt  = buildInputsOnlyPrompt(questionId, spec, method, numTests);
            String payload = LLMConfig.buildTextPayload(prompt, mapper);
            HttpRequest req = LLMConfig.addAuthHeaders(
                    HttpRequest.newBuilder()
                            .uri(URI.create(LLMConfig.buildUrl(apiKey)))
                            .timeout(Duration.ofSeconds(LLMConfig.TIMEOUT_S)),
                    apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return Collections.emptyList();
            return parseInputsResponse(LLMConfig.extractText(res.body(), mapper), method, numTests, spec);
        } catch (Exception e) {
            System.err.println("LLM input generation failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private String buildInputsOnlyPrompt(
        String questionId, QuestionSpec spec, MethodSpec method, int numTests) {

        String mainSource = spec.getSourceLines() != null && !spec.getSourceLines().isEmpty()
                ? String.join("\n", spec.getSourceLines()).replace("%", "%%")
                : "// No source available";

        StringBuilder p = new StringBuilder();
        // UPDATE: Explicitly request REAL CONCRETE inputs
        p.append("Generate exactly ").append(numTests)
        .append(" sets of REAL, CONCRETE INPUT ARGUMENTS for this Java method. Do NOT include expected values.\n\n");

        // UPDATE: Strict warning against placeholders to fix the "%s" issue
        p.append("CRITICAL: Do NOT use placeholders like \"%s\", \"arg1\", \"value\", \"hello\", or \"world\" unless they are explicitly required by the source.\n");
        p.append("If SOURCE main() already contains concrete example method calls, copy those concrete arguments exactly.\n");
        p.append("Return ONLY a JSON array:\n[{\"args\":[\"arg1\",\"arg2\"],\"rationale\":\"...\"}]\n\n");

        // --- NEW DYNAMIC SECTION: This fixes Q1a/Q1b ---
        if (spec.getDescription() != null && !spec.getDescription().isBlank()) {
            p.append("=== CRITICAL EXAM REQUIREMENTS (MUST FOLLOW) ===\n");
            p.append("You MUST generate inputs that test the specific logic rules defined here:\n");
            p.append(spec.getDescription()).append("\n\n");
        }

        // --- KEEP: Strict Syntax Rules ---
        p.append("CRITICAL JAVA SYNTAX RULES:\n");
        p.append("1. PERFECT GENERICS: If a parameter is ArrayList<Integer>, you MUST ONLY use numbers. NEVER insert booleans or strings into a numeric list.\n");
        p.append("2. FLAT OBJECTS: Keep object instantiations simple. DO NOT nest constructors deeply.\n");
        p.append("3. BALANCED BRACKETS: Ensure all parentheses are perfectly closed.\n");
        p.append("4. String: \\\"value\\\"  - int: 42  - double: 3.14  - boolean: true/false\n");
        p.append("5. ArrayList: new java.util.ArrayList<>(java.util.Arrays.asList(1, 2))\n\n");

        // --- KEEP: Data Files Section ---
        if (spec.hasDataFiles()) {
            p.append("=== DATA FILES IN THIS QUESTION FOLDER ===\n");
            p.append("IMPORTANT: The method reads from files. Use ONLY these exact filenames.\n");
            for (Map.Entry<String, String> df : spec.getDataFiles().entrySet()) {
                p.append("FILE: ").append(df.getKey()).append("\n");
                String[] lines = df.getValue().split("\n");
                int show = Math.min(lines.length, 25);
                for (int i = 0; i < show; i++) p.append("  ").append(lines[i]).append("\n");
                if (lines.length > show) p.append("  ... (").append(lines.length - show).append(" more rows)\n");
                p.append("\n");
            }
            p.append("Rules for file-based inputs:\n");
            p.append("  - arg1 (filename) must be one of the filenames above\n");
            p.append("  - arg2 must be a real value present in that file\n");
            p.append("  - Include one test with a non-existent filename to test error handling\n\n");
        }

        String abstractWarning = buildAbstractClassWarning(spec);
        if (!abstractWarning.isBlank()) p.append(abstractWarning).append("\n\n");

        p.append("METHOD: ").append(buildMethodSignature(method)).append("\n\n");
        p.append("SOURCE:\n").append(mainSource).append("\n\n");

        if (!spec.getSupportingSourceFiles().isEmpty()) {
            p.append("SUPPORTING FILES:\n");
            for (Map.Entry<String, String> e : spec.getSupportingSourceFiles().entrySet()) {
                p.append("--- ").append(e.getKey()).append(" ---\n");
                p.append(e.getValue().replace("%", "%%")).append("\n");
            }
        }

        p.append("Return ONLY the JSON array with ").append(numTests).append(" elements.\n");
        return p.toString();
    }
    // =========================================================================
    // Phase 2: Template oracle (FIX 1: copies data files into temp dir)
    // =========================================================================

    private List<GeneratedTestCase> deriveExpectedFromTemplate(
            String questionId, QuestionSpec spec, MethodSpec method,
            List<List<String>> inputSets) {
        try {
            Path tempDir = Files.createTempDirectory("oracle_" + questionId + "_");
            try {
                copyTemplateSourceToDir(spec, tempDir);
                // FIX 1: copy data files so file-reading methods can find them
                for (Map.Entry<String, String> df : spec.getDataFiles().entrySet()) {
                    Files.writeString(tempDir.resolve(df.getKey()), df.getValue());
                }

                String driverClass = "OracleDriver_" + questionId;
                Files.writeString(tempDir.resolve(driverClass + ".java"),
                        buildOracleDriver(questionId, spec, method, inputSets));

                if (!compileDir(tempDir)) return smokeTestsForInputs(inputSets);
                return parseOracleOutput(runClass(driverClass, tempDir), inputSets, method);
            } finally {
                deleteDirectory(tempDir);
            }
        } catch (Exception e) {
            System.err.println("Oracle error for " + questionId + ": " + e.getMessage());
            return smokeTestsForInputs(inputSets);
        }
    }

    private void copyTemplateSourceToDir(QuestionSpec spec, Path tempDir) throws Exception {
        if (spec.getSourceLines() != null && !spec.getSourceLines().isEmpty()) {
            String src = stripPackageDeclaration(String.join("\n", spec.getSourceLines()));
            String cn  = spec.getClassName() != null ? spec.getClassName() : "Q";
            Files.writeString(tempDir.resolve(cn + ".java"), src);
        }
        for (Map.Entry<String, String> e : spec.getSupportingSourceFiles().entrySet()) {
            if (!e.getKey().endsWith(".java")) continue;
            Files.writeString(tempDir.resolve(e.getKey()),
                    stripPackageDeclaration(e.getValue()));
        }
    }

    private String buildOracleDriver(String questionId, QuestionSpec spec,
            MethodSpec method, List<List<String>> inputSets) {
        String studentClass = spec.getClassName() != null ? spec.getClassName() : questionId;
        boolean canExtend   = canExtend(spec);
        StringBuilder sb    = new StringBuilder();
        sb.append("import java.util.*;\n\n");
        sb.append("public class OracleDriver_").append(questionId);
        if (canExtend && !method.isStatic()) sb.append(" extends ").append(studentClass);
        sb.append(" {\n\n    public static void main(String[] args) {\n");

        for (int i = 0; i < inputSets.size(); i++) {
            sb.append("        System.out.println(\"===ORACLE_START:").append(i).append("===\");\n");
            sb.append("        try {\n");
            List<String> callArgs = buildCallArgs(sb, inputSets.get(i), method, i);
            String callExpr = canExtend && !method.isStatic()
                    ? "new OracleDriver_" + questionId + "()." + method.getName()
                      + "(" + String.join(", ", callArgs) + ")"
                    : studentClass + "." + method.getName()
                      + "(" + String.join(", ", callArgs) + ")";
            if (method.returnsVoid()) {
                sb.append("            ").append(callExpr).append(";\n");
                sb.append("            System.out.println(\"__VOID__\");\n");
            } else {
                sb.append("            Object __r = ").append(callExpr).append(";\n");
                sb.append("            System.out.println(__r);\n");
            }
            sb.append("        } catch (Exception __e) {\n");
            sb.append("            System.out.println(\"__EXCEPTION__:\" + __e.getClass().getSimpleName());\n");
            sb.append("        }\n");
            sb.append("        System.out.println(\"===ORACLE_END:").append(i).append("===\");\n");
        }
        sb.append("    }\n}\n");
        return sb.toString();
    }

    private List<String> buildCallArgs(StringBuilder sb, List<String> rawArgs,
            MethodSpec method, int seed) {
        List<String> callArgs = new ArrayList<>();
        List<ParamSpec> params = method.getParams();
        for (int j = 0; j < params.size(); j++) {
            String argExpr = j < rawArgs.size() ? rawArgs.get(j) : "null";
            String pType   = params.get(j).getType();
            String varName = "arg_" + seed + "_" + j;
            if (isListType(pType)) {
                String inner = argExpr
                        .replaceFirst("new\\s+java\\.util\\.ArrayList<[^>]*>\\(", "")
                        .replaceFirst("java\\.util\\.Arrays\\.asList\\(", "")
                        .replaceAll("\\)\\)$", "");
                sb.append("            ArrayList<").append(extractGenericType(pType))
                  .append("> ").append(varName).append(" = new ArrayList<>(Arrays.asList(")
                  .append(inner).append("));\n");
                callArgs.add(varName);
            } else {
                callArgs.add(argExpr);
            }
        }
        return callArgs;
    }

    private boolean compileDir(Path dir) {
        try {
            StringBuilder cmd = new StringBuilder("javac -cp \"")
                    .append(dir.toAbsolutePath()).append("\" -d \"")
                    .append(dir.toAbsolutePath()).append("\" -encoding UTF-8 -nowarn");
            try (var walk = Files.walk(dir)) {
                walk.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> cmd.append(" \"").append(p.toAbsolutePath()).append("\""));
            }
            ProcessBuilder pb = isWindows()
                    ? new ProcessBuilder("cmd", "/c", cmd.toString())
                    : new ProcessBuilder("sh", "-c", cmd.toString());
            pb.directory(dir.toFile()); pb.redirectErrorStream(true);
            Process proc = pb.start();
            String out = new String(proc.getInputStream().readAllBytes());
            boolean ok = proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
                         && proc.exitValue() == 0;
            if (!ok) System.err.println("[Oracle] " + out.substring(0, Math.min(400, out.length())));
            return ok;
        } catch (Exception e) { return false; }
    }

    private String runClass(String className, Path workingDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-Xmx64m", "-cp", workingDir.toAbsolutePath().toString(), className);
            pb.directory(workingDir.toFile()); pb.redirectErrorStream(true);
            Process proc = pb.start();
            String out = new String(proc.getInputStream().readAllBytes());
            proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            if (proc.isAlive()) proc.destroyForcibly();
            return out;
        } catch (Exception e) { return ""; }
    }

    private List<GeneratedTestCase> parseOracleOutput(String output,
            List<List<String>> inputSets, MethodSpec method) {
        List<GeneratedTestCase> cases = new ArrayList<>();
        String retType = method.getReturnType();
        for (int i = 0; i < inputSets.size(); i++) {
            String sm = "===ORACLE_START:" + i + "===";
            String em = "===ORACLE_END:"   + i + "===";
            int si = output.indexOf(sm), ei = output.indexOf(em);
            if (si < 0 || ei <= si) {
                cases.add(GeneratedTestCase.smokeTest(inputSets.get(i), "Oracle block missing")); continue;
            }
            String block = output.substring(si + sm.length(), ei).trim();
            if (block.startsWith("__VOID__")) {
                cases.add(new GeneratedTestCase(inputSets.get(i), null, true, false, "Void", "void_check")); continue;
            }
            if (block.startsWith("__EXCEPTION__")) {
                cases.add(GeneratedTestCase.smokeTest(inputSets.get(i), "Exception thrown")); continue;
            }
            cases.add(new GeneratedTestCase(inputSets.get(i),
                    buildExpectedExpr(block, retType), false, false,
                    "Oracle-derived", resolveStrategyForType(retType)));
        }
        return cases;
    }

    private String buildExpectedExpr(String raw, String retType) {
        raw = raw.trim();
        if (!isPrimitive(retType) && !"String".equals(retType))
            return "\"" + raw.replace("\\","\\\\").replace("\"","\\\"") + "\"";
        if ("String".equals(retType))
            return "\"" + raw.replace("\\","\\\\").replace("\"","\\\"") + "\"";
        try { Double.parseDouble(raw); return raw; } catch (NumberFormatException ignored) {}
        if ("true".equals(raw) || "false".equals(raw)) return raw;
        return "\"" + raw.replace("\\","\\\\").replace("\"","\\\"") + "\"";
    }

    private String resolveStrategyForType(String retType) {
        if (isListType(retType)) return "toString_equals";
        if ("double".equals(retType) || "float".equals(retType)) return "eq_float";
        if ("int".equals(retType) || "long".equals(retType) || "boolean".equals(retType)) return "==";
        if ("String".equals(retType)) return "equals";
        return "toString_equals";
    }

    // =========================================================================
    // Parse LLM inputs response
    // =========================================================================

    private List<List<String>> parseInputsResponse(
            String llmText, MethodSpec method, int numTests, QuestionSpec spec) {
        List<List<String>> result = new ArrayList<>();
        try {
            String c = llmText.trim().replaceAll("```[a-zA-Z]*\\n?","").trim();
            int as = c.lastIndexOf('['), ae = c.lastIndexOf(']');
            if (as >= 0 && ae > as) c = c.substring(as, ae + 1);
            List<Map<String, Object>> raw = mapper.readValue(c, new TypeReference<>() {});
            for (Map<String, Object> e : raw) {
                List<String> args = castToStringList(e.get("args"));
                if (args.size() != method.getParams().size()) continue;
                boolean bad = false;
                for (int i = 0; i < args.size(); i++) {
                    String type = method.getParams().get(i).getType();
                    if (!"String".equals(type)) continue;
                    if (isPlaceholderStringArg(args.get(i))) {
                        bad = true;
                        break;
                    }
                }
                if (!bad) result.add(args);
            }
        } catch (Exception e) {
            System.err.println("Could not parse LLM inputs: " + e.getMessage());
        }
        if (result.isEmpty() && spec != null && spec.hasDataFiles() && method.getParams().size() == 2
                && "String".equals(method.getParams().get(0).getType())
                && "String".equals(method.getParams().get(1).getType())) {
            result.addAll(buildDataFileFallbackInputs(spec, numTests));
        }
        return result;
    }

    private boolean isPlaceholderStringArg(String rawArg) {
        String normalized = rawArg == null ? "" : rawArg.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.isBlank()
                || lower.equals("%s")
                || lower.equals("arg1")
                || lower.equals("arg2")
                || lower.equals("value")
                || lower.equals("hello")
                || lower.equals("world")
                || lower.startsWith("placeholder");
    }

    private List<List<String>> buildDataFileFallbackInputs(QuestionSpec spec, int numTests) {
        List<List<String>> fallbacks = new ArrayList<>();
        Map.Entry<String, String> first = spec.getDataFiles().entrySet().stream().findFirst().orElse(null);
        if (first == null) return fallbacks;

        String filename = first.getKey();
        Set<String> tokens = new LinkedHashSet<>();
        String[] lines = first.getValue().split("\\R");
        for (String line : lines) {
            for (String token : line.split("[,;\\t ]+")) {
                String t = token.trim();
                if (t.matches("[A-Za-z][A-Za-z0-9_-]{1,}")) tokens.add(t);
                if (tokens.size() >= Math.max(3, numTests)) break;
            }
            if (tokens.size() >= Math.max(3, numTests)) break;
        }

        if (tokens.isEmpty()) tokens.add("UNKNOWN");
        for (String t : tokens) {
            fallbacks.add(List.of("\"" + filename + "\"", "\"" + t + "\""));
            if (fallbacks.size() >= Math.max(1, numTests - 1)) break;
        }
        fallbacks.add(List.of("\"nosuchfile.txt\"", "\"" + tokens.iterator().next() + "\""));
        return fallbacks;
    }

    private String resolveStringArgument(String block, String argExpr) {
        String expr = argExpr == null ? "" : argExpr.trim();
        if (expr.startsWith("\"")) return expr;
        Pattern varPat = Pattern.compile("String\\s+" + Pattern.quote(expr) + "\\s*=\\s*\"([^\"]*)\";");
        Matcher varM = varPat.matcher(block);
        if (varM.find()) return "\"" + varM.group(1) + "\"";
        return "\"" + expr.replace("\"", "") + "\"";
    }

    private List<String> splitCallArgs(String raw) {
        List<String> parts = new ArrayList<>();
        if (raw == null || raw.isBlank()) return parts;
        int depth = 0;
        boolean inQuote = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '"' && (i == 0 || raw.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            }
            if (!inQuote) {
                if (ch == '(') depth++;
                if (ch == ')') depth = Math.max(0, depth - 1);
                if (ch == ',' && depth == 0) {
                    parts.add(cur.toString());
                    cur.setLength(0);
                    continue;
                }
            }
            cur.append(ch);
        }
        if (!cur.isEmpty()) parts.add(cur.toString());
        return parts;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<GeneratedTestCase> smokeTestsForInputs(List<List<String>> inputSets) {
        List<GeneratedTestCase> r = new ArrayList<>();
        for (List<String> ins : inputSets) r.add(GeneratedTestCase.smokeTest(ins, "Smoke fallback"));
        return r;
    }

    private List<GeneratedTestCase> padWithSmoke(List<GeneratedTestCase> existing,
            int targetSize, MethodSpec method) {
        List<GeneratedTestCase> result = new ArrayList<>(existing);
        int seed = existing.size();
        while (result.size() < targetSize) {
            List<String> da = new ArrayList<>();
            for (ParamSpec p : method.getParams()) da.add(safeDefault(p.getType(), seed));
            result.add(GeneratedTestCase.smokeTest(da, "Padding smoke " + seed++));
        }
        return result;
    }

    private String stripPackageDeclaration(String src) {
        return src.replaceFirst("(?m)^\\s*package\\s+[^;]+;", "// [package removed]");
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
        return sb.append(")").toString();
    }

    private String buildAbstractClassWarning(QuestionSpec spec) {
        List<String> abs = new ArrayList<>();
        for (Map.Entry<String, String> e : spec.getSupportingSourceFiles().entrySet()) {
            Matcher m = Pattern.compile("(?:abstract class|interface)\\s+(\\w+)").matcher(e.getValue());
            while (m.find()) abs.add(m.group(1));
        }
        if (abs.isEmpty()) return "";
        return "NEVER instantiate these abstract/interfaces: " + String.join(", ", abs)
                + "\nUse concrete subclasses instead.";
    }

    private boolean canExtend(QuestionSpec spec) {
        return !spec.hasParameterisedConstructor() || spec.getFields().isEmpty();
    }

    private boolean isListType(String type) {
        if (type == null) return false;
        String t = type.trim();
        return t.startsWith("List") || t.startsWith("ArrayList")
                || t.startsWith("java.util.List") || t.startsWith("java.util.ArrayList");
    }

    private boolean isPrimitive(String type) {
        return Set.of("int","long","double","float","boolean","char","byte","short")
                  .contains(type == null ? "" : type.trim());
    }

    private String extractGenericType(String type) {
        if (type == null) return "Object";
        int lt = type.indexOf('<'), gt = type.lastIndexOf('>');
        return (lt >= 0 && gt > lt) ? type.substring(lt + 1, gt).trim() : "Object";
    }

    private String safeDefault(String type, int seed) {
        if (type == null) return "null";
        String t = type.trim();
        if (isListType(t)) {
            String elem = extractGenericType(t);
            if ("String".equals(elem)) return "new java.util.ArrayList<>(java.util.Arrays.asList(\"hello\",\"world\"))";
            if ("Integer".equals(elem)||"int".equals(elem)) return "new java.util.ArrayList<>(java.util.Arrays.asList(1,2,3))";
            return "new java.util.ArrayList<>()";
        }
        return switch (t) {
            case "int","Integer"    -> String.valueOf(seed%10+1);
            case "long","Long"      -> seed+"L";
            case "double","Double"  -> seed+".0";
            case "float","Float"    -> seed+".0f";
            case "boolean","Boolean"-> seed%2==0?"true":"false";
            case "char","Character" -> "'a'";
            case "String"           -> "\"hello\"";
            default                 -> "null";
        };
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private void deleteDirectory(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private List<String> castToStringList(Object obj) {
        if (obj instanceof List<?> list) {
            List<String> r = new ArrayList<>();
            for (Object item : list) r.add(item==null?"null":item.toString());
            return r;
        }
        return List.of(obj==null?"null":obj.toString());
    }
}