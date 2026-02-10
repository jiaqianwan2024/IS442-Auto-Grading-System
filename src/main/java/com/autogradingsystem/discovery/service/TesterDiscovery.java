package com.autogradingsystem.discovery.service;

import com.autogradingsystem.discovery.model.TesterMap;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * TesterDiscovery - Discovers Tester Files
 * 
 * PURPOSE:
 * - Automatically discovers tester files in resources/input/testers/
 * - No hardcoded tester lists needed
 * - Extracts question ID from tester filename
 * 
 * NAMING CONVENTION:
 * - Testers must end with "Tester.java"
 * - Question ID = filename without "Tester.java" suffix
 * - Examples:
 *   Q1aTester.java → Q1a
 *   Q1bTester.java → Q1b
 *   Q2aTester.java → Q2a
 *   Q3Tester.java → Q3
 * 
 * WHY THIS MATTERS:
 * - Add new tester? Just place Q4aTester.java in testers/ folder
 * - No code changes needed
 * - System discovers automatically
 * 
 * CHANGES FROM v3.0:
 * - Removed verbose logging (handled by DiscoveryController/Main.java)
 * - Accepts tester directory path as parameter
 * - Cleaner method signatures
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class TesterDiscovery {
    
    /**
     * Discovers all tester files in the specified directory
     * 
     * WORKFLOW:
     * 1. Scan directory for files ending with "Tester.java"
     * 2. For each tester file:
     *    a. Extract question ID (remove "Tester.java" suffix)
     *    b. Store mapping: questionId → testerFilename
     * 3. Build and return TesterMap
     * 
     * EXAMPLE:
     * Input directory contains:
     *   Q1aTester.java
     *   Q1bTester.java
     *   Q2aTester.java
     *   Q3Tester.java
     * 
     * Output TesterMap:
     *   Q1a → Q1aTester.java
     *   Q1b → Q1bTester.java
     *   Q2a → Q2aTester.java
     *   Q3 → Q3Tester.java
     * 
     * @param testersDir Path to directory containing tester files
     * @return TesterMap containing all discovered testers
     * @throws IOException if directory cannot be read or doesn't exist
     */
    public TesterMap discoverTesters(Path testersDir) throws IOException {
        
        // Validate directory exists
        if (!Files.exists(testersDir)) {
            throw new IOException(
                "Testers directory not found: " + testersDir + "\n" +
                "Please create the directory and place tester files there"
            );
        }
        
        if (!Files.isDirectory(testersDir)) {
            throw new IOException(
                "Path is not a directory: " + testersDir
            );
        }
        
        // Map to store question ID → tester filename
        Map<String, String> testerMapping = new HashMap<>();
        
        // Scan directory for *Tester.java files
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(testersDir, "*Tester.java")) {
            
            for (Path testerFile : stream) {
                
                String filename = testerFile.getFileName().toString();
                
                // Extract question ID from filename
                String questionId = extractQuestionId(filename);
                
                if (questionId != null) {
                    testerMapping.put(questionId, filename);
                }
            }
        }
        
        // Validate at least one tester found
        if (testerMapping.isEmpty()) {
            throw new IOException(
                "No tester files found in: " + testersDir + "\n" +
                "Expected files like: Q1aTester.java, Q1bTester.java, etc."
            );
        }
        
        // Build and return TesterMap
        return new TesterMap(testerMapping);
    }
    
    /**
     * Extracts question ID from tester filename
     * 
     * LOGIC:
     * - Remove "Tester.java" suffix
     * - What's left is the question ID
     * 
     * EXAMPLES:
     * - Q1aTester.java → Q1a ✅
     * - Q1bTester.java → Q1b ✅
     * - Q2aTester.java → Q2a ✅
     * - Q3Tester.java → Q3 ✅
     * - RandomFile.java → null ❌ (doesn't end with Tester.java)
     * 
     * VALIDATION:
     * - Returns null if filename doesn't end with "Tester.java"
     * - This filters out non-tester files in the directory
     * 
     * @param filename Tester filename
     * @return Question ID, or null if not a valid tester filename
     */
    private String extractQuestionId(String filename) {
        
        // Check if filename ends with "Tester.java"
        if (!filename.endsWith("Tester.java")) {
            return null;
        }
        
        // Remove "Tester.java" suffix to get question ID
        // Example: "Q1aTester.java" → "Q1a"
        String questionId = filename.substring(0, filename.length() - "Tester.java".length());
        
        // Validate question ID is not empty
        if (questionId.isEmpty()) {
            return null;
        }
        
        return questionId;
    }
    
    /**
     * Validates that a filename follows tester naming convention
     * 
     * CONVENTION:
     * - Must end with "Tester.java"
     * - Must have non-empty question ID prefix
     * 
     * EXAMPLES:
     * - Q1aTester.java ✅
     * - MyTestTester.java ✅ (valid, but won't match any question)
     * - Tester.java ❌ (no question ID)
     * - Q1a.java ❌ (doesn't end with Tester.java)
     * 
     * @param filename Filename to validate
     * @return true if valid tester filename, false otherwise
     */
    public boolean isValidTesterFilename(String filename) {
        return extractQuestionId(filename) != null;
    }
}