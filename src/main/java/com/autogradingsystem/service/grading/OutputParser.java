package com.autogradingsystem.service.grading;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OutputParser {

    // -----------------------------------------------------
    // REGEX PATTERN (The Search Rule)
    // -----------------------------------------------------
    // This defines what a "Score" looks like to the computer.
    // \\d+       -> One or more digits (e.g., "10")
    // (\\.\\d+)? -> Optional decimal part (e.g., ".0" or ".5")
    // This matches "10", "3.0", "0.5", etc.
    private static final Pattern SCORE_PATTERN = Pattern.compile("(\\d+(\\.\\d+)?)");

    /**
     * Scans the console output to find the final score.
     * @param consoleOutput The full text printed by the Student + Tester.
     * @return The parsed score as a double (e.g., 5.0). Returns 0.0 if failed.
     */
    public double parseScore(String consoleOutput) {
        // Safety Check: If output is empty (e.g., crash before print), score is 0.
        if (consoleOutput == null || consoleOutput.isEmpty()) {
            return 0.0;
        }

        // -----------------------------------------------------
        // STRATEGY: "LAST LINE"
        // -----------------------------------------------------
        // We assume the Tester file prints the score as the VERY LAST thing.
        // 1. Trim whitespace (remove blank lines at end).
        // 2. Split into array of lines.
        // 3. Grab the last line.
        String[] lines = consoleOutput.trim().split("\\n");
        String lastLine = lines[lines.length - 1].trim();

        // -----------------------------------------------------
        // PARSING LOGIC
        // -----------------------------------------------------
        // Apply the Regex to that last line.
        Matcher matcher = SCORE_PATTERN.matcher(lastLine);
        
        if (matcher.find()) {
            try {
                // Group(1) contains the actual number string found by Regex.
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                // Should never happen if Regex is correct, but good for safety.
                System.err.println("[Parser] Could not parse number: " + lastLine);
            }
        }

        // If no number is found on the last line, give 0.
        return 0.0;
    }
}