package com.autogradingsystem.testcasegenerator.model;

import java.util.*;

/**
 * QuestionSpec - Describes the inferred public API of a single student question file.
 *
 * PACKAGE: com.autogradingsystem.testcasegenerator.model
 *
 * v3.5: Added supportingSourceFiles — all other .java files in the same question
 * folder (e.g. Shape.java, Rectangle.java, Circle.java for Q3). These are passed
 * to the LLM so it knows about abstract classes, concrete subclasses, constructors,
 * and helper classes when generating test cases.
 */
public class QuestionSpec {

    private String className;
    private final List<MethodSpec> methods = new ArrayList<>();
    private final List<FieldSpec>  fields  = new ArrayList<>();
    private boolean hasParameterisedConstructor = false;
    private List<String> sourceLines = Collections.emptyList();

    /**
     * All OTHER .java source files in the same question folder.
     * Key   = filename (e.g. "Shape.java")
     * Value = full source text
     *
     * Used by LLMTestOracle to understand abstract classes, concrete subclasses,
     * and helper classes when generating test cases.
     */
    private Map<String, String> supportingSourceFiles = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Getters / setters
    // -------------------------------------------------------------------------

    public String getClassName()                          { return className; }
    public void   setClassName(String className)          { this.className = className; }

    public List<MethodSpec> getMethods()                  { return methods; }
    public void addMethod(MethodSpec m)                   { methods.add(m); }

    public List<FieldSpec> getFields()                    { return fields; }
    public void addField(FieldSpec f)                     { fields.add(f); }

    public boolean hasParameterisedConstructor()          { return hasParameterisedConstructor; }
    public void setHasParameterisedConstructor(boolean v) { this.hasParameterisedConstructor = v; }

    public List<String> getSourceLines()                  { return sourceLines; }
    public void setSourceLines(List<String> lines)        { this.sourceLines = lines; }

    public Map<String, String> getSupportingSourceFiles() { return supportingSourceFiles; }
    public void setSupportingSourceFiles(Map<String, String> files) {
        this.supportingSourceFiles = files;
    }
    public void addSupportingFile(String filename, String source) {
        this.supportingSourceFiles.put(filename, source);
    }

    // =========================================================================
    // Inner models
    // =========================================================================

    public static class MethodSpec {
        private final String name;
        private final String returnType;
        private final List<ParamSpec> params;
        private final boolean isStatic;

        public MethodSpec(String name, String returnType,
                          List<ParamSpec> params, boolean isStatic) {
            this.name       = name;
            this.returnType = returnType;
            this.params     = params != null ? params : Collections.emptyList();
            this.isStatic   = isStatic;
        }

        public String          getName()        { return name; }
        public String          getReturnType()  { return returnType; }
        public List<ParamSpec> getParams()      { return params; }
        public boolean         isStatic()       { return isStatic; }
        public boolean         returnsVoid()    { return "void".equals(returnType); }

        private static final Set<String> PRIMITIVES =
                Set.of("int","long","double","float","boolean","char","byte","short");
        public boolean returnsPrimitive() { return PRIMITIVES.contains(returnType); }
    }

    public static class ParamSpec {
        private final String type;
        private final String name;
        public ParamSpec(String type, String name) { this.type = type; this.name = name; }
        public String getType() { return type; }
        public String getName() { return name; }
    }

    public static class FieldSpec {
        private final String type;
        private final String name;
        public FieldSpec(String type, String name) { this.type = type; this.name = name; }
        public String getType() { return type; }
        public String getName() { return name; }
    }
}