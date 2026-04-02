package com.autogradingsystem.execution.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OutputParser - Parses Scores from Tester Output
 *
 * PURPOSE:
 * - Extracts numeric score from tester output
 * - Expects score on last line of output
 * - Handles both integer and decimal scores
 * 
 * TESTER OUTPUT FORMAT:
 * Expected output from testers:
 * ```
 * Test 1: PASS
 * Test 2: PASS
 * Test 3: FAIL
 * 2.0
 * ```
 * Last line contains the score: 2.0
 * 
 * PARSING STRATEGY:
 * 1. Split output into lines
 * 2. Get last line
 * 3. Extract first number from last line using regex
 * 4. Parse as double
 * 5. Return score
 * 
 */
 
public class OutputParser {

    /**
     * Matches a signed decimal/integer.
     * Examples matched: "3", "3.0", "0.75", "-1.0", "10"
     */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");

    /**
     * Matches explicit score labels, case-insensitive.
     * Examples: "Score: 3.0", "Total: 3.0", "Points: 3.0", "Result: 3.0"
     */
    private static final Pattern SCORE_LABEL_PATTERN =
            Pattern.compile("(?i)(?:final_score|score|total|points?|result)\\s*[=:]\\s*(-?\\d+(\\.\\d+)?)");

    /**
     * Parses the numeric score from tester output.
     *
     * STRATEGY (in order of priority):
     * 1. If output is TIMEOUT/ERROR → return 0.0 immediately
     * 2. Scan all non-empty lines from BOTTOM UP for an explicit score label
     *    (e.g., "Score: 3.0") — most reliable signal
     * 3. Fall back to last non-empty line, take the LAST number on it
     * 4. If nothing found → 0.0
     * 5. Floor result at 0.0 (negative scores not allowed)
     *
     * @param output Raw tester output string
     * @return Parsed score ≥ 0.0
     */
    public double parseScore(String output) {

        if (output == null || output.isBlank()) return 0.0;

        // Normalise line endings
        String normalised = output.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalised.split("\n");

        // ── PASS 0: THE NINJA MOVE (Partial Credit Rescue) ───────────────
        // Scan top-down to find the highest injected partial score before a crash
        double highestPartial = -1.0;
        for (String line : lines) {
            if (line.trim().startsWith("PARTIAL_SCORE:")) {
                try {
                    highestPartial = Double.parseDouble(line.split(":")[1].trim());
                } catch (Exception ignored) {}
            }
        }
        
        if (highestPartial >= 0.0) {
            return highestPartial; // Immediately return the rescued score!
        }

        // ── PASS 1: Look for explicit score labels (Bottom-Up) ───────────
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("[SYSTEM]") || line.startsWith("TIMEOUT")) continue;

            Matcher labelMatcher = SCORE_LABEL_PATTERN.matcher(line);
            if (labelMatcher.find()) {
                double val = parseDouble(labelMatcher.group(1));
                return Math.max(val, 0.0);
            }
        }

        // ── PASS 2: Fall back to last non-empty line (Bottom-Up) ─────────
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            // Crucial Fix: Ignore the system messages so it doesn't read timestamps/limits!
            if (line.isEmpty() || line.startsWith("[SYSTEM]") || line.startsWith("TIMEOUT")) continue;

            Double lastNumber = findLastNumber(line);
            if (lastNumber != null) {
                return Math.max(lastNumber, 0.0);
            }
        }

        return 0.0;
    }

    /**
     * Highest {@code PARTIAL_SCORE:} value from injected tester lines, or 0.0 if none.
     * Used for TIMEOUT recovery — must not use full {@link #parseScore} there, because lines like
     * {@code Test 5: getAverageAge(...)} would be misread as score 5.0 (last number on the line).
     */
    public double parseHighestPartialScore(String output) {
        if (output == null || output.isBlank()) return 0.0;
        String normalised = output.replace("\r\n", "\n").replace("\r", "\n");
        double highest = -1.0;
        for (String line : normalised.split("\n")) {
            if (line.trim().startsWith("PARTIAL_SCORE:")) {
                try {
                    double v = Double.parseDouble(line.split(":", 2)[1].trim());
                    highest = Math.max(highest, v);
                } catch (Exception ignored) { }
            }
        }
        return highest >= 0.0 ? highest : 0.0;
    }

    /**
     * Returns true if the output contains a parseable score.
     */
    public boolean hasValidScore(String output) {
        if (output == null || output.isBlank()) return false;
        String upper = output.toUpperCase();
        if (upper.startsWith("TIMEOUT") || upper.startsWith("ERROR")) return false;

        String normalised = output.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalised.split("\n");

        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (SCORE_LABEL_PATTERN.matcher(line).find()) return true;
            if (findLastNumber(line) != null) return true;
        }
        return false;
    }

    /**
     * Returns every number found in the output (useful for debugging).
     */
    public double[] extractAllNumbers(String output) {
        if (output == null || output.isBlank()) return new double[0];
        java.util.List<Double> numbers = new java.util.ArrayList<>();
        Matcher m = NUMBER_PATTERN.matcher(output);
        while (m.find()) {
            Double d = parseDoubleOrNull(m.group());
            if (d != null) numbers.add(d);
        }
        return numbers.stream().mapToDouble(Double::doubleValue).toArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns the LAST number found on a line, or null if none. */
    private Double findLastNumber(String line) {
        Matcher m = NUMBER_PATTERN.matcher(line);
        Double last = null;
        while (m.find()) {
            Double d = parseDoubleOrNull(m.group());
            if (d != null) last = d;
        }
        return last;
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private Double parseDoubleOrNull(String s) {
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return null; }
    }
}