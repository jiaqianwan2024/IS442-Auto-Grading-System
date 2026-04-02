package com.autogradingsystem.testcasegenerator.service;

import com.autogradingsystem.testcasegenerator.model.GeneratedTestCase;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec.MethodSpec;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec.ParamSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LLMTestOracleSupport {

    private LLMTestOracleSupport() {
    }

    static List<GeneratedTestCase> smokeTestsForInputs(List<List<String>> inputSets) {
        List<GeneratedTestCase> result = new ArrayList<>();
        for (List<String> inputs : inputSets) {
            result.add(GeneratedTestCase.smokeTest(inputs, "Smoke fallback"));
        }
        return result;
    }

    static List<GeneratedTestCase> padWithSmoke(List<GeneratedTestCase> existing,
                                                int targetSize,
                                                MethodSpec method) {
        List<GeneratedTestCase> result = new ArrayList<>(existing);
        int seed = existing.size();
        while (result.size() < targetSize) {
            List<String> defaults = new ArrayList<>();
            for (ParamSpec param : method.getParams()) {
                defaults.add(safeDefault(param.getType(), seed));
            }
            result.add(GeneratedTestCase.smokeTest(defaults, "Padding smoke " + seed));
            seed++;
        }
        return result;
    }

    static String stripPackageDeclaration(String src) {
        return src.replaceFirst("(?m)^\\s*package\\s+[^;]+;", "// [package removed]");
    }

    static String buildMethodSignature(MethodSpec method) {
        StringBuilder sb = new StringBuilder("public ");
        if (method.isStatic()) {
            sb.append("static ");
        }
        sb.append(method.getReturnType()).append(" ").append(method.getName()).append("(");
        List<ParamSpec> params = method.getParams();
        for (int i = 0; i < params.size(); i++) {
            sb.append(params.get(i).getType()).append(" ").append(params.get(i).getName());
            if (i < params.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.append(")").toString();
    }

    static String buildAbstractClassWarning(QuestionSpec spec) {
        List<String> abstractTypes = new ArrayList<>();
        for (Map.Entry<String, String> entry : spec.getSupportingSourceFiles().entrySet()) {
            Matcher matcher = Pattern.compile("(?:abstract class|interface)\\s+(\\w+)").matcher(entry.getValue());
            while (matcher.find()) {
                abstractTypes.add(matcher.group(1));
            }
        }
        if (abstractTypes.isEmpty()) {
            return "";
        }
        return "NEVER instantiate these abstract/interfaces: " + String.join(", ", abstractTypes)
                + "\nUse concrete subclasses instead.";
    }

    static boolean canExtend(QuestionSpec spec) {
        return !spec.hasParameterisedConstructor() || spec.getFields().isEmpty();
    }

    static void deleteDirectory(Path dir) {
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
        } catch (Exception ignored) {
        }
    }

    static boolean isListType(String type) {
        if (type == null) {
            return false;
        }
        String t = type.trim();
        return t.startsWith("List") || t.startsWith("ArrayList")
                || t.startsWith("java.util.List") || t.startsWith("java.util.ArrayList");
    }

    static boolean isPrimitive(String type) {
        String value = type == null ? "" : type.trim();
        return value.equals("int")
                || value.equals("long")
                || value.equals("double")
                || value.equals("float")
                || value.equals("boolean")
                || value.equals("char")
                || value.equals("byte")
                || value.equals("short");
    }

    static String extractGenericType(String type) {
        if (type == null) {
            return "Object";
        }
        int lt = type.indexOf('<');
        int gt = type.lastIndexOf('>');
        return (lt >= 0 && gt > lt) ? type.substring(lt + 1, gt).trim() : "Object";
    }

    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    static String resolveStrategyForType(String retType) {
        if (isListType(retType)) {
            return "toString_equals";
        }
        if ("double".equals(retType) || "float".equals(retType)) {
            return "eq_float";
        }
        if ("int".equals(retType) || "long".equals(retType) || "boolean".equals(retType)) {
            return "==";
        }
        if ("String".equals(retType)) {
            return "equals";
        }
        return "toString_equals";
    }

    static List<String> castToStringList(Object obj) {
        if (obj instanceof List<?>) {
            List<?> list = (List<?>) obj;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(item == null ? "null" : item.toString());
            }
            return result;
        }
        List<String> single = new ArrayList<>();
        single.add(obj == null ? "null" : obj.toString());
        return single;
    }

    private static String safeDefault(String type, int seed) {
        if (type == null) {
            return "null";
        }
        String t = type.trim();
        if (isListType(t)) {
            String elem = extractGenericType(t);
            if ("String".equals(elem)) {
                return "new java.util.ArrayList<>(java.util.Arrays.asList(\"hello\",\"world\"))";
            }
            if ("Integer".equals(elem) || "int".equals(elem)) {
                return "new java.util.ArrayList<>(java.util.Arrays.asList(1,2,3))";
            }
            return "new java.util.ArrayList<>()";
        }

        switch (t) {
            case "int":
            case "Integer":
                return String.valueOf(seed % 10 + 1);
            case "long":
            case "Long":
                return seed + "L";
            case "double":
            case "Double":
                return seed + ".0";
            case "float":
            case "Float":
                return seed + ".0f";
            case "boolean":
            case "Boolean":
                return seed % 2 == 0 ? "true" : "false";
            case "char":
            case "Character":
                return "'a'";
            case "String":
                return "\"hello\"";
            default:
                return "null";
        }
    }

    static String oracleSafeDefault(String type, int seed) {
        return safeDefault(type, seed);
    }
}
