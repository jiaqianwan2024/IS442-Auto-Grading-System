package com.autogradingsystem.testcasegenerator.service;

import com.autogradingsystem.testcasegenerator.model.QuestionSpec;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec.FieldSpec;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec.MethodSpec;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec.ParamSpec;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * QuestionSpecParser - Parses a template .java file to extract its public API.
 *
 * PACKAGE: com.autogradingsystem.testcasegenerator.service
 * PURPOSE: Infer class name, public methods, constructors, and fields from
 *          a student question template stub.
 *
 * Uses regex-based line scanning — no external dependencies.
 * Handles the common patterns found in IS442 assignment question files:
 *   - Public class declaration → class name
 *   - Public methods (instance and static) with typed parameters
 *   - Public constructors → hasParameterisedConstructor flag
 *   - Instance fields → used to infer constructor args when no explicit
 *     constructor is present in the stub
 *
 * v3.5 NOTE: Unchanged — the LLM oracle receives the full source lines
 * (spec.getSourceLines()) in addition to the parsed method list, so the
 * parser just needs to keep capturing everything it already does.
 */
public class QuestionSpecParser {

    // -------------------------------------------------------------------------
    // Regex patterns
    // -------------------------------------------------------------------------

    private static final Pattern CLASS_DECL = Pattern.compile(
            "^\\s*public\\s+class\\s+(\\w+)");

    private static final Pattern METHOD_DECL = Pattern.compile(
            "^\\s*public\\s+(static\\s+)?(final\\s+)?(\\w[\\w<>\\[\\],\\s]*)\\s+(\\w+)\\s*\\(([^)]*)\\)");

    private static final Pattern CTOR_DECL = Pattern.compile(
            "^\\s*public\\s+(\\w+)\\s*\\(([^)]*)\\)");

    private static final Pattern FIELD_DECL = Pattern.compile(
            "^\\s*(?:private|protected|public)?\\s*(\\w[\\w<>\\[\\]]*(?:\\s+\\w[\\w<>\\[\\]]*)?)\\s+(\\w+)\\s*(?:=.*)?;");

    // -------------------------------------------------------------------------
    // Java keywords
    // -------------------------------------------------------------------------

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract","assert","boolean","break","byte","case","catch","char","class",
            "const","continue","default","do","double","else","enum","extends","final",
            "finally","float","for","goto","if","implements","import","instanceof","int",
            "interface","long","native","new","package","private","protected","public",
            "return","short","static","strictfp","super","switch","synchronized","this",
            "throw","throws","transient","try","void","volatile","while","true","false","null"
    );

    // -------------------------------------------------------------------------

    /**
     * Parses the given template source file and returns a populated {@link QuestionSpec}.
     *
     * @param templateFile  path to a .java template / stub file
     * @return populated spec (never null; may have empty methods list if parsing fails)
     * @throws IOException on file read failure
     */
    public QuestionSpec parse(Path templateFile) throws IOException {
        List<String> lines = Files.readAllLines(templateFile);
        QuestionSpec spec  = new QuestionSpec();
        spec.setSourceLines(lines);  // full source passed to LLMTestOracle

        String  detectedClassName = null;
        boolean inBlockComment    = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();

            if (inBlockComment) {
                if (line.contains("*/")) inBlockComment = false;
                continue;
            }
            if (line.startsWith("/*")) { inBlockComment = true; continue; }
            if (line.startsWith("//")) continue;
            if (line.isEmpty())        continue;

            // ── Class declaration ──────────────────────────────────────────
            Matcher classMatcher = CLASS_DECL.matcher(rawLine);
            if (classMatcher.find() && detectedClassName == null) {
                detectedClassName = classMatcher.group(1);
                spec.setClassName(detectedClassName);
                continue;
            }

            if (detectedClassName == null) continue;

            // ── Method declaration ─────────────────────────────────────────
            Matcher methodMatcher = METHOD_DECL.matcher(rawLine);
            if (methodMatcher.find()) {
                boolean isStatic  = methodMatcher.group(1) != null;
                String returnType = methodMatcher.group(3).trim().replaceAll("\\s+", " ");
                String methodName = methodMatcher.group(4).trim();
                String rawParams  = methodMatcher.group(5).trim();

                if (methodName.equals(detectedClassName)) continue;
                if (JAVA_KEYWORDS.contains(methodName))   continue;

                List<ParamSpec> params = parseParams(rawParams);
                spec.addMethod(new MethodSpec(methodName, returnType, params, isStatic));
                continue;
            }

            // ── Constructor detection ──────────────────────────────────────
            Matcher ctorMatcher = CTOR_DECL.matcher(rawLine);
            if (ctorMatcher.find() && ctorMatcher.group(1).equals(detectedClassName)) {
                String rawParams = ctorMatcher.group(2).trim();
                if (!rawParams.isEmpty()) {
                    spec.setHasParameterisedConstructor(true);
                }
                continue;
            }

            // ── Field declaration ──────────────────────────────────────────
            Matcher fieldMatcher = FIELD_DECL.matcher(rawLine);
            if (fieldMatcher.find()) {
                String type = fieldMatcher.group(1).trim();
                String name = fieldMatcher.group(2).trim();
                if (!JAVA_KEYWORDS.contains(type) && !JAVA_KEYWORDS.contains(name)) {
                    spec.addField(new FieldSpec(type, name));
                }
            }
        }

        if (spec.getClassName() == null) {
            String fname = templateFile.getFileName().toString();
            spec.setClassName(fname.replace(".java", ""));
        }

        return spec;
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private List<ParamSpec> parseParams(String rawParams) {
        List<ParamSpec> result = new ArrayList<>();
        if (rawParams == null || rawParams.isBlank()) return result;

        for (String token : rawParams.split(",")) {
            String part = token.trim();
            if (part.isEmpty()) continue;
            part = part.replaceAll("^final\\s+", "");
            int lastSpace = part.lastIndexOf(' ');
            if (lastSpace > 0) {
                String type = part.substring(0, lastSpace).trim();
                String name = part.substring(lastSpace + 1).trim();
                result.add(new ParamSpec(type, name));
            }
        }
        return result;
    }
}