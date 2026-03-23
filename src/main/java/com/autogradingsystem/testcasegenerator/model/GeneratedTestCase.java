package com.autogradingsystem.testcasegenerator.model;

import java.util.List;

/**
 * GeneratedTestCase - One LLM-generated input→expected pair for a single test case.
 *
 * PACKAGE: com.autogradingsystem.testcasegenerator.model
 * PURPOSE: Carries the structured output of LLMTestOracle for one test case.
 *          TesterGenerator renders a list of these into a compilable *Tester.java.
 *
 * FIELDS:
 *   args        — ordered list of Java literal strings, one per method parameter.
 *                 e.g. ["new java.util.ArrayList<>(java.util.Arrays.asList(\"hello\",\"level\"))"]
 *                 Each string is a valid Java expression that can be pasted directly
 *                 into generated source.
 *
 *   expected    — Java literal string for the expected return value.
 *                 e.g. "new java.util.ArrayList<>(java.util.Arrays.asList(\"hello\",\"level\"))"
 *                 null means the method is expected to return null.
 *
 *   isVoidCheck — true when the method returns void; in this case `expected` is
 *                 ignored and the test only checks that no exception is thrown.
 *
 *   isSmokeTest — true when LLMTestOracle could not produce a verifiable expected
 *                 value (e.g. API failure, low-confidence result, complex object
 *                 return with no equality contract).  TesterGenerator renders a
 *                 no-exception check instead of an equality assertion.
 *
 *   rationale   — human-readable explanation of why this input/expected pair is
 *                 correct.  Written into a comment in the generated tester so
 *                 instructors can audit what the LLM understood.
 *
 *   equalityStrategy — how TesterGenerator should compare actual vs expected:
 *                 "==" for primitives, "eq_float" for float/double, "equals" for
 *                 objects, "list_equals" for ordered lists, "set_equals" for
 *                 unordered collections.
 */
public class GeneratedTestCase {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private List<String> args;
    private String       expected;
    private boolean      isVoidCheck  = false;
    private boolean      isSmokeTest  = false;
    private String       rationale    = "";
    private String       equalityStrategy = "equals"; // default

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public GeneratedTestCase() {}

    /** Full constructor used by LLMTestOracle after JSON parsing. */
    public GeneratedTestCase(List<String> args, String expected,
                             boolean isVoidCheck, boolean isSmokeTest,
                             String rationale, String equalityStrategy) {
        this.args             = args;
        this.expected         = expected;
        this.isVoidCheck      = isVoidCheck;
        this.isSmokeTest      = isSmokeTest;
        this.rationale        = rationale;
        this.equalityStrategy = equalityStrategy;
    }

    /** Convenience factory for a smoke-test (no expected value). */
    public static GeneratedTestCase smokeTest(List<String> args, String rationale) {
        GeneratedTestCase tc = new GeneratedTestCase();
        tc.args        = args;
        tc.isSmokeTest = true;
        tc.rationale   = rationale;
        return tc;
    }

    /** Convenience factory for a void method test. */
    public static GeneratedTestCase voidCheck(List<String> args, String rationale) {
        GeneratedTestCase tc = new GeneratedTestCase();
        tc.args        = args;
        tc.isVoidCheck = true;
        tc.rationale   = rationale;
        return tc;
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public List<String> getArgs()                      { return args; }
    public void         setArgs(List<String> args)     { this.args = args; }

    public String  getExpected()                       { return expected; }
    public void    setExpected(String expected)        { this.expected = expected; }

    public boolean isVoidCheck()                       { return isVoidCheck; }
    public void    setVoidCheck(boolean v)             { this.isVoidCheck = v; }

    public boolean isSmokeTest()                       { return isSmokeTest; }
    public void    setSmokeTest(boolean v)             { this.isSmokeTest = v; }

    public String  getRationale()                      { return rationale; }
    public void    setRationale(String rationale)      { this.rationale = rationale; }

    public String  getEqualityStrategy()               { return equalityStrategy; }
    public void    setEqualityStrategy(String s)       { this.equalityStrategy = s; }
}