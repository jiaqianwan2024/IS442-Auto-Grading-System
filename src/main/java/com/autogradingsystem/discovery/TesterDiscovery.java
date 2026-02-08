package com.autogradingsystem.discovery;

import com.autogradingsystem.model.TesterMap;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * TesterDiscovery - Automatic Tester File Detection and Mapping
 * 
 * PURPOSE:
 * - Scans src/main/resources/testers/ for tester files
 * - Extracts question ID from tester filename
 * - Maps question IDs to their corresponding tester files
 * - Builds TesterMap without any hardcoding
 * 
 * WHY WE NEED THIS:
 * - Eliminates hardcoded tester mappings in ExecutionController
 * - Automatically detects new tester files when added
 * - Follows naming convention to match testers to questions
 * 
 * NAMING CONVENTION:
 * - Tester files follow pattern: [QuestionId]Tester.java
 * - Examples:
 *   * Q1aTester.java → Question ID: Q1a
 *   * Q2bTester.java → Question ID: Q2b
 *   * Q3Tester.java  → Question ID: Q3
 * 
 * CROSS-PLATFORM NOTES:
 * - Uses java.nio.file.Path API (works on Windows, Mac, Linux)
 * - DirectoryStream for efficient file iteration
 * - Handles different path separators automatically
 * 
 * EXAMPLE INPUT:
 * src/main/resources/testers/
 * ├── Q1aTester.java
 * ├── Q1bTester.java
 * ├── Q2aTester.java
 * ├── Q2bTester.java
 * └── Q3Tester.java
 * 
 * EXAMPLE OUTPUT:
 * TesterMap {
 *   testers: {
 *     "Q1a": "Q1aTester.java",
 *     "Q1b": "Q1bTester.java",
 *     "Q2a": "Q2aTester.java",
 *     "Q2b": "Q2bTester.java",
 *     "Q3": "Q3Tester.java"
 *   }
 * }
 * 
 * @author IS442 Team
 * @version 1.0
 */
public class TesterDiscovery {
    
    /**
     * Discovers all tester files and maps them to question IDs.
     * 
     * WORKFLOW:
     * 1. Scan testers directory for files ending with "Tester.java"
     * 2. For each tester file:
     *    a. Extract filename
     *    b. Extract question ID from filename
     *    c. Add to map: questionId → testerFile
     * 3. Build and return TesterMap
     * 
     * CROSS-PLATFORM HANDLING:
     * - Path.resolve() handles path separators automatically
     * - DirectoryStream works on all platforms
     * - Path.getFileName() returns just the filename
     * 
     * ERROR HANDLING:
     * - If testers directory doesn't exist → logs warning, returns empty map
     * - If tester filename doesn't match pattern → logs warning, skips file
     * - Never crashes - graceful degradation
     * 
     * @param testersDirectory Path to testers directory (e.g., src/main/resources/testers)
     * @return TesterMap object with all discovered testers
     * @throws IOException if directory cannot be read (but only if it exists)
     */
    public TesterMap discoverTesters(Path testersDirectory) throws IOException {
        
        System.out.println("\n🔍 Discovering tester files...");
        System.out.println("   Location: " + testersDirectory);
        
        // Initialize map to store testers
        Map<String, String> testersMap = new HashMap<>();
        
        // STEP 1: Check if testers directory exists
        if (!Files.exists(testersDirectory)) {
            System.err.println("   ⚠️  Testers directory not found: " + testersDirectory);
            System.err.println("   Returning empty tester map - all questions will score 0");
            return new TesterMap(testersMap);  // Return empty map
        }
        
        // STEP 2: Scan directory for tester files
        // Using try-with-resources to auto-close DirectoryStream
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(testersDirectory)) {
            
            for (Path testerFile : stream) {
                
                // Only process regular files (skip directories)
                if (Files.isRegularFile(testerFile)) {
                    
                    String filename = testerFile.getFileName().toString();
                    
                    // STEP 2a: Check if file matches tester naming convention
                    // Must end with "Tester.java"
                    if (filename.endsWith("Tester.java")) {
                        
                        // STEP 2b: Extract question ID from filename
                        // "Q1aTester.java" → "Q1a"
                        String questionId = extractQuestionId(filename);
                        
                        if (questionId != null) {
                            // STEP 2c: Add to map
                            testersMap.put(questionId, filename);
                            
                            // Log discovered tester
                            System.out.println("   ✅ " + filename + " → " + questionId);
                            
                        } else {
                            // Filename doesn't match expected pattern
                            System.err.println("   ⚠️  Could not extract question ID from: " + filename);
                        }
                    }
                }
            }
        }
        
        // STEP 3: Log summary
        System.out.println("\n   📊 Total: " + testersMap.size() + " tester(s) found");
        
        // STEP 4: Return TesterMap
        return new TesterMap(testersMap);
    }
    
    /**
     * Extracts question ID from tester filename.
     * 
     * CONVENTION:
     * - Tester files are named: [QuestionID]Tester.java
     * - Remove "Tester.java" suffix to get question ID
     * 
     * EXAMPLES:
     * - "Q1aTester.java" → "Q1a" ✅
     * - "Q2bTester.java" → "Q2b" ✅
     * - "Q3Tester.java"  → "Q3" ✅
     * - "Q10aTester.java" → "Q10a" ✅
     * - "RandomTester.java" → "Random" ⚠️ (valid but unexpected)
     * - "NotATester.java" → null ❌ (doesn't end with Tester.java)
     * 
     * VALIDATION:
     * - Checks that filename ends with "Tester.java"
     * - Checks that there's something before "Tester.java"
     * - Returns null if validation fails
     * 
     * WHY THIS WORKS:
     * - Simple string manipulation
     * - No complex regex needed
     * - Easy to understand and maintain
     * 
     * @param filename Tester filename (e.g., "Q1aTester.java")
     * @return Question ID (e.g., "Q1a") or null if invalid format
     */
    private String extractQuestionId(String filename) {
        
        // Check if filename ends with "Tester.java"
        if (!filename.endsWith("Tester.java")) {
            return null;  // Invalid format
        }
        
        // Remove "Tester.java" suffix
        // "Q1aTester.java" → "Q1a"
        // Length of "Tester.java" is 11 characters
        String questionId = filename.substring(0, filename.length() - "Tester.java".length());
        
        // Validate that we have something left
        // If filename was just "Tester.java", questionId would be empty
        if (questionId.isEmpty()) {
            return null;  // Invalid - no question ID
        }
        
        // Additional validation: Question ID should start with Q and have a number
        // This is optional but helps catch mistakes
        if (!questionId.matches("^Q\\d+[a-z]?$")) {
            // Pattern: Q + digits + optional lowercase letter
            // Q1 ✅, Q1a ✅, Q10b ✅
            // Random ❌, Q ❌, Qa ❌
            System.err.println("   ⚠️  Warning: '" + questionId + "' doesn't match expected pattern (Q + number + optional letter)");
            // Still return it - maybe it's intentional
        }
        
        return questionId;
    }
    
    /**
     * Alternative discovery method that accepts String path.
     * Convenience method for backward compatibility.
     * 
     * @param testersDirectoryPath String path to testers directory
     * @return TesterMap object with discovered testers
     * @throws IOException if directory cannot be read
     */
    public TesterMap discoverTesters(String testersDirectoryPath) throws IOException {
        return discoverTesters(Paths.get(testersDirectoryPath));
    }
}