package com.autogradingsystem.testcasegenerator.service;

import com.autogradingsystem.testcasegenerator.model.GeneratedTestCase;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec.MethodSpec;

import java.util.*;
import java.util.regex.*;

/**
 * TesterAugmentor — appends AI-generated test cases into an existing *Tester.java.
 *
 * PACKAGE: com.autogradingsystem.testcasegenerator.service
 *
 * Used by the Test Case Studio tab when the examiner uploads existing tester files.
 * Counts the test cases already present in the file, then asks the LLM to generate
 * (targetCount - existingCount) additional ones and splices them into the grade() body
 * before the closing brace.
 *
 * If existingCount >= targetCount, the file is returned unchanged.
 * If no existing source is provided, delegates entirely to TesterGenerator.
 */
public class TesterAugmentor {

    private final LLMTestOracle   oracle;
    private final TesterGenerator generator;

    // Matches each { try { ... } } test-case block inside grade()
    private static final Pattern TC_BLOCK = Pattern.compile(
            "\\{\\s*\\n\\s*try\\s*\\{", Pattern.DOTALL);

    public TesterAugmentor() {
        this.oracle    = LLMTestOracle.fromEnvironment();
        this.generator = new TesterGenerator(oracle);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Given an existing tester source (may be null/blank) and a question spec,
     * returns a tester source that has at least {@code targetCount} test cases.
     *
     * @param questionId   e.g. "Q1a"
     * @param spec         parsed question spec from the template ZIP
     * @param existingSource  current *Tester.java source, or null/blank if none
     * @param targetCount  number of AI test cases to add (= marks for this question)
     * @return augmented (or freshly generated) tester source
     */
    public String augment(String questionId, QuestionSpec spec,
                          String existingSource, int targetCount) {

        boolean hasExisting = existingSource != null && !existingSource.isBlank();

        if (!hasExisting) {
            // No existing file — generate from scratch
            System.out.println("  🤖 [Augmentor] " + questionId
                    + ": no existing tester — generating " + targetCount + " from scratch.");
            return generator.generate(questionId, spec, targetCount);
        }

        // Always generate exactly targetCount (= marks) MORE cases on top of existing.
        int existing   = countTestCases(existingSource);
        int toGenerate = targetCount; // always the full mark count, regardless of existing
        System.out.println("  [Augmentor] " + questionId
                + ": found " + existing + " existing - appending " + toGenerate + " more (= marks).");

        List<MethodSpec> testable = generator.getTestableMethods(spec);
        if (testable.isEmpty()) {
            System.out.println("  ⚠️  [Augmentor] " + questionId
                    + ": no testable methods found — returning existing source unchanged.");
            return existingSource;
        }

        List<GeneratedTestCase> newCases =
                oracle.generateTestCases(questionId, spec, testable, toGenerate);

        if (newCases.isEmpty()) {
            System.out.println("  ⚠️  [Augmentor] " + questionId
                    + ": LLM returned no cases — returning existing source unchanged.");
            return existingSource;
        }

        // Render the new test case blocks as source snippets
        String newBlocks = renderAdditionalBlocks(
                newCases, testable, spec.getClassName(), existing, targetCount);

        // Splice into existing source just before the closing brace of grade()
        return spliceIntoGrade(existingSource, newBlocks, questionId);
    }

    // -------------------------------------------------------------------------
    // Count existing { try { } } blocks in the grade() body
    // -------------------------------------------------------------------------

    public int countTestCases(String source) {
        if (source == null || source.isBlank()) return 0;
        // Find grade() method body
        int gradeIdx = source.indexOf("public static void grade()");
        if (gradeIdx < 0) gradeIdx = 0;
        String gradeBody = source.substring(gradeIdx);
        Matcher m = TC_BLOCK.matcher(gradeBody);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    // -------------------------------------------------------------------------
    // Render additional test case blocks as raw Java source
    // Uses TesterGenerator's internal rendering logic by generating a full
    // temp tester and extracting just the new { ... } blocks from it.
    // -------------------------------------------------------------------------

    private String renderAdditionalBlocks(List<GeneratedTestCase> cases,
                                           List<MethodSpec> testable,
                                           String studentClass,
                                           int existingCount,
                                           int targetCount) {

        // Build a minimal temporary tester for just the new cases
        // We reuse TesterGenerator but with a synthetic spec for only the new cases
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < cases.size(); i++) {
            GeneratedTestCase tc = cases.get(i);
            MethodSpec method = testable.get(i % testable.size());

            sb.append("        // AI-generated test case ").append(existingCount + i + 1).append("\n");
            sb.append("        {\n");
            sb.append("            try {\n");

            // Build arg literals
            List<String> args = tc.getArgs() != null ? tc.getArgs() : List.of();
            String callArgs = String.join(", ", args);
            String callExpr = studentClass + "." + method.getName() + "(" + callArgs + ")";

            sb.append("                System.out.printf(\"Test %d: ")
              .append(method.getName()).append("(")
              .append(args.isEmpty() ? "" : "%s").append(")%n\", tcNum++")
              .append(args.isEmpty() ? "" : ", " + (args.size() == 1 ? args.get(0) : "\"" + callArgs + "\""))
              .append(");\n");

            String strategy = tc.getEqualityStrategy() != null ? tc.getEqualityStrategy() : "equals";
            String retType  = method.getReturnType();
            String javaRet  = javaType(retType);

            if (tc.isSmokeTest() || tc.isVoidCheck() || method.returnsVoid()) {
                sb.append("                ").append(callExpr).append(";\n");
                sb.append("                System.out.println(\"Expected  :|no exception|\");\n");
                sb.append("                System.out.println(\"Actual    :|no exception|\");\n");
                sb.append("                score += 1;\n");
                sb.append("                System.out.println(\"Passed\");\n");
                sb.append("            } catch (Exception e) {\n");
                sb.append("                System.out.println(\"Expected  :|no exception|\");\n");
                sb.append("                System.out.println(\"Actual    :|exception: \" + e.getClass().getSimpleName() + \"|\");\n");
                sb.append("                System.out.println(\"Failed\");\n");
            } else if ("toString_equals".equals(strategy)) {
                String exp = tc.getExpected() != null ? tc.getExpected() : "\"\"";
                sb.append("                String expected").append(i).append(" = ").append(exp).append(";\n");
                sb.append("                ").append(javaRet).append(" result").append(i)
                  .append(" = ").append(callExpr).append(";\n");
                sb.append("                System.out.printf(\"Expected  :|%s|%n\", expected").append(i).append(");\n");
                sb.append("                System.out.printf(\"Actual    :|%s|%n\", result").append(i).append(");\n");
                sb.append("                if (result").append(i).append(" != null && result").append(i)
                  .append(".toString().equals(expected").append(i).append(")) {\n");
                sb.append("                    score += 1;\n");
                sb.append("                    System.out.println(\"Passed\");\n");
                sb.append("                } else { System.out.println(\"Failed\"); }\n");
                sb.append("            } catch (Exception e) {\n");
                sb.append("                System.out.println(\"Failed -> Exception: \" + e.getMessage());\n");
            } else if ("list_equals".equals(strategy) || isListType(retType)) {
                String exp = tc.getExpected() != null ? tc.getExpected() : "new java.util.ArrayList<>()";
                sb.append("                ").append(javaRet).append(" expected").append(i)
                  .append(" = ").append(exp).append(";\n");
                sb.append("                ").append(javaRet).append(" result").append(i)
                  .append(" = ").append(callExpr).append(";\n");
                sb.append("                System.out.printf(\"Expected  :|%s|%n\", expected").append(i).append(");\n");
                sb.append("                System.out.printf(\"Actual    :|%s|%n\", result").append(i).append(");\n");
                sb.append("                if (expected").append(i).append(".equals(result").append(i).append(")) {\n");
                sb.append("                    score += 1;\n");
                sb.append("                    System.out.println(\"Passed\");\n");
                sb.append("                } else { System.out.println(\"Failed\"); }\n");
                sb.append("            } catch (Exception e) {\n");
                sb.append("                System.out.println(\"Failed -> Exception\");\n");
                sb.append("                e.printStackTrace();\n");
            } else {
                String exp = tc.getExpected() != null ? tc.getExpected() : "null";
                sb.append("                ").append(javaRet).append(" expected").append(i)
                  .append(" = ").append(exp).append(";\n");
                sb.append("                ").append(javaRet).append(" result").append(i)
                  .append(" = ").append(callExpr).append(";\n");
                sb.append("                System.out.printf(\"Expected  :|%s|%n\", expected").append(i).append(");\n");
                sb.append("                System.out.printf(\"Actual    :|%s|%n\", result").append(i).append(");\n");
                String cond = buildCondition("result" + i, "expected" + i, strategy, javaRet);
                sb.append("                if (").append(cond).append(") {\n");
                sb.append("                    score += 1;\n");
                sb.append("                    System.out.println(\"Passed\");\n");
                sb.append("                } else { System.out.println(\"Failed\"); }\n");
                sb.append("            } catch (Exception e) {\n");
                sb.append("                System.out.println(\"Failed -> Exception\");\n");
                sb.append("                e.printStackTrace();\n");
            }

            sb.append("            }\n");
            sb.append("            System.out.println(\"-------------------------------------------------------\");\n");
            sb.append("        }\n\n");
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Splice new blocks into the existing source just before grade()'s closing }
    // -------------------------------------------------------------------------

    private String spliceIntoGrade(String source, String newBlocks, String questionId) {
        // Find grade() method, then find its last closing brace
        int gradeIdx = source.indexOf("public static void grade()");
        if (gradeIdx < 0) {
            System.err.println("⚠️  [Augmentor] Could not find grade() in " + questionId + " — appending before class end.");
            // Fallback: insert before the very last }
            int lastBrace = source.lastIndexOf('}');
            if (lastBrace < 0) return source + "\n" + newBlocks;
            return source.substring(0, lastBrace) + newBlocks + source.substring(lastBrace);
        }

        // Find the opening { of grade()
        int gradeOpen = source.indexOf('{', gradeIdx);
        if (gradeOpen < 0) return source;

        // Walk forward to find the matching closing } of grade()
        int depth = 1, i = gradeOpen + 1;
        while (i < source.length() && depth > 0) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            i++;
        }
        // i is now one past the closing } of grade()
        int gradeClose = i - 1; // position of the closing }

        // Insert new blocks just before the closing } of grade()
        return source.substring(0, gradeClose)
                + "\n"
                + newBlocks
                + source.substring(gradeClose);
    }

    // -------------------------------------------------------------------------
    // Type helpers (duplicated from TesterGenerator to keep this class standalone)
    // -------------------------------------------------------------------------

    private boolean isListType(String type) {
        if (type == null) return false;
        String t = type.trim();
        return t.startsWith("List") || t.startsWith("ArrayList")
                || t.startsWith("java.util.List") || t.startsWith("java.util.ArrayList");
    }

    private String javaType(String raw) {
        if (raw == null) return "Object";
        return switch (raw.trim()) {
            case "Integer"   -> "int";
            case "Long"      -> "long";
            case "Double"    -> "double";
            case "Float"     -> "float";
            case "Boolean"   -> "boolean";
            case "Character" -> "char";
            default          -> raw.trim();
        };
    }

    private String buildCondition(String actual, String expected, String strategy, String javaRet) {
        return switch (strategy) {
            case "=="          -> actual + " == " + expected;
            case "eq_float"    -> "eq(" + actual + ", " + expected + ")";
            case "list_equals" -> "listEq(" + actual + ", " + expected + ")";
            case "set_equals"  -> "setEq(" + actual + ", " + expected + ")";
            default -> {
                boolean prim = Set.of("int","long","boolean","char","byte","short")
                                  .contains(javaRet.trim());
                boolean fp   = "double".equals(javaRet.trim()) || "float".equals(javaRet.trim());
                if (fp)   yield "eq(" + actual + ", " + expected + ")";
                if (prim) yield actual + " == " + expected;
                yield "eq((Object)" + actual + ", (Object)" + expected + ")";
            }
        };
    }
}
