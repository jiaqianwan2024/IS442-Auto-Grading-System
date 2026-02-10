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
 * NO CHANGES FROM v3.0:
 * - Already well-designed
 * - Package declaration updated
 * - No logic changes needed
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class OutputParser {
    
    /**
     * Regex pattern to match numbers (integer or decimal)
     * 
     * PATTERN EXPLANATION:
     * (\\d+(\\.\\d+)?)
     * - \\d+ = one or more digits (e.g., "3" or "10")
     * - (\\.\\d+)? = optional decimal part (e.g., ".5" or ".75")
     * - Full match examples: "3", "3.0", "10.5", "0.75"
     * 
     * CAPTURES:
     * - Group 1: Complete number (e.g., "3.0")
     * - This is what we extract and parse
     */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(\\.\\d+)?)");
    
    /**
     * Parses score from tester output
     * 
     * ALGORITHM:
     * 1. Split output by newlines
     * 2. Get last non-empty line
     * 3. Find first number in that line
     * 4. Parse as double
     * 5. Return score (or 0.0 if parsing fails)
     * 
     * EXAMPLES:
     * 
     * Input: "Test 1: PASS\nTest 2: PASS\n3.0"
     * Output: 3.0
     * 
     * Input: "Test 1: FAIL\n0"
     * Output: 0.0
     * 
     * Input: "Score: 2.5/3.0"
     * Output: 2.5 (first number found)
     * 
     * Input: "ERROR: No score"
     * Output: 0.0 (no number found)
     * 
     * EDGE CASES HANDLED:
     * - Empty output → 0.0
     * - No numbers found → 0.0
     * - Multiple numbers on last line → Uses first number
     * - Decimal scores → Parsed correctly
     * - Integer scores → Converted to double (3 → 3.0)
     * 
     * @param output Complete output from tester execution
     * @return Parsed score as double (0.0 if parsing fails)
     */
    public double parseScore(String output) {
        
        // Handle null or empty output
        if (output == null || output.trim().isEmpty()) {
            return 0.0;
        }
        
        // Split output into lines
        String[] lines = output.split("\n");
        
        if (lines.length == 0) {
            return 0.0;
        }
        
        // Get last line (where score should be)
        String lastLine = lines[lines.length - 1].trim();
        
        if (lastLine.isEmpty()) {
            // Last line empty - try second-to-last
            if (lines.length > 1) {
                lastLine = lines[lines.length - 2].trim();
            } else {
                return 0.0;
            }
        }
        
        // Find first number in last line
        Matcher matcher = NUMBER_PATTERN.matcher(lastLine);
        
        if (matcher.find()) {
            try {
                // Extract and parse the number
                String scoreStr = matcher.group(1);
                return Double.parseDouble(scoreStr);
                
            } catch (NumberFormatException e) {
                // Parsing failed - return 0.0
                return 0.0;
            }
        }
        
        // No number found
        return 0.0;
    }
    
    /**
     * Validates that output contains a parseable score
     * 
     * USEFUL FOR:
     * - Pre-validation before parsing
     * - Checking tester output format
     * 
     * @param output Tester output to validate
     * @return true if output contains a parseable score, false otherwise
     */
    public boolean hasValidScore(String output) {
        
        if (output == null || output.trim().isEmpty()) {
            return false;
        }
        
        String[] lines = output.split("\n");
        if (lines.length == 0) {
            return false;
        }
        
        String lastLine = lines[lines.length - 1].trim();
        
        Matcher matcher = NUMBER_PATTERN.matcher(lastLine);
        return matcher.find();
    }
    
    /**
     * Extracts all numbers from output (for debugging)
     * 
     * USEFUL FOR:
     * - Debugging score parsing issues
     * - Understanding tester output format
     * 
     * @param output Tester output
     * @return Array of all numbers found in output
     */
    public double[] extractAllNumbers(String output) {
        
        if (output == null || output.trim().isEmpty()) {
            return new double[0];
        }
        
        Matcher matcher = NUMBER_PATTERN.matcher(output);
        
        java.util.List<Double> numbers = new java.util.ArrayList<>();
        
        while (matcher.find()) {
            try {
                double num = Double.parseDouble(matcher.group(1));
                numbers.add(num);
            } catch (NumberFormatException e) {
                // Skip invalid numbers
            }
        }
        
        // Convert List to array
        double[] result = new double[numbers.size()];
        for (int i = 0; i < numbers.size(); i++) {
            result[i] = numbers.get(i);
        }
        
        return result;
    }
}