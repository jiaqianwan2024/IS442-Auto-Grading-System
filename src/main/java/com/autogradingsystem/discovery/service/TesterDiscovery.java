package com.autogradingsystem.discovery.service;

import com.autogradingsystem.discovery.model.TesterMap;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * TesterDiscovery - Discovers Tester Files
 * * PURPOSE:
 * - Automatically discovers tester files in resources/input/testers/
 * - No hardcoded tester lists needed
 * - Extracts question ID from tester filename
 * * NAMING CONVENTION:
 * - Testers must end with "Tester.java"
 * - Question ID = filename without "Tester.java" suffix
 * - Examples:
 * Q1aTester.java → Q1a
 * Q1bTester.java → Q1b
 * Q2aTester.java → Q2a
 * Q3Tester.java → Q3
 * * WHY THIS MATTERS:
 * - Add new tester? Just place Q4aTester.java in testers/ folder
 * - No code changes needed
 * - System discovers automatically
 * * CHANGES FROM v3.0:
 * - Removed verbose logging (handled by DiscoveryController/Main.java)
 * - Accepts tester directory path as parameter
 * - Cleaner method signatures
 * * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class TesterDiscovery {
    
    /**
     * Discovers all tester files in the specified directory
     * * WORKFLOW:
     * 1. Scan directory for files ending with "Tester.java"
     * 2. For each tester file:
     * a. Extract question ID (remove "Tester.java" suffix)
     * b. Store mapping: questionId → testerFilename
     * 3. Build and return TesterMap
     * * EXAMPLE:
     * Input directory contains:
     * Q1aTester.java
     * Q1bTester.java
     * Q2aTester.java
     * Q3Tester.java
     * * Output TesterMap:
     * Q1a → Q1aTester.java
     * Q1b → Q1bTester.java
     * Q2a → Q2aTester.java
     * Q3 → Q3Tester.java
     * * @param testersDir Path to directory containing tester files
     * @return TesterMap containing all discovered testers
     * @throws IOException if directory cannot be read or doesn't exist
     */
    public TesterMap discoverTesters(Path testersDir) throws IOException {
        // Validate directory exists
        if (!Files.exists(testersDir)) {
            throw new IOException("Testers directory not found: " + testersDir);
        }
        
        if (!Files.isDirectory(testersDir)) {
            throw new IOException("Path is not a directory: " + testersDir);
        }
        
        System.out.println("   🔍 Scanning Testers Directory: " + testersDir);
        Map<String, String> testerMapping = new HashMap<>();
        
        // Filter to *Tester.java only (not all *.java) to prevent stray files like
        // Q1a.java accidentally placed in the testers directory from being registered
        // as a tester and silently overwriting the real Q1aTester.java mapping.
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(testersDir, "*Tester.java")) {
            for (Path testerFile : stream) {
                String filename = testerFile.getFileName().toString();
                String rawId = extractQuestionId(filename); // Uses your regex
                
                if (rawId != null) {
                    // Professional Registration: Result "q1a" or "Q1A" -> "Q1a"
                    String formattedId = "Q" + rawId.substring(1).toLowerCase();

                    // FINAL-5 fix: detect duplicate question IDs before registering.
                    // Without this, Q1aTester.java and Q1ATester.java both map to "Q1a"
                    // and the second one silently overwrites the first with no warning.
                    if (testerMapping.containsKey(formattedId)) {
                        System.out.println("      ⚠️  [DUPLICATE] " + filename
                            + ": Question ID '" + formattedId + "' already registered by '"
                            + testerMapping.get(formattedId) + "'. Skipping — remove one of them.");
                    } else {
                        testerMapping.put(formattedId, filename);
                        System.out.println("      🎯 Registered Tester: [" + filename + "] for Question ID: [" + formattedId + "]");
                    }
                } else {
                    System.out.println("      ⚠️  [IGNORED] " + filename + ": Non-tester file detected.");
                }
            }
        }
        
        if (testerMapping.isEmpty()) {
            throw new IOException("No valid tester files found.");
        }
        
        System.out.println("   ✅ Tester Discovery Complete. Found " + testerMapping.size() + " valid tester(s).");
        return new TesterMap(testerMapping);
    }
    
    /**
     * Extracts question ID from tester filename
     * 
     * LOGIC:
     * - File must end with exactly "Tester.java" (enforced by DirectoryStream filter)
     * - Strip "Tester.java" suffix to get the question ID prefix
     * - Prefix must match ^Q\d+[a-z]?$ (e.g. Q1a, Q1b, Q2a, Q3)
     * 
     * EXAMPLES:
     * - Q1aTester.java  → strips suffix → "Q1a"  → matches pattern ✅ → returns "q1a"
     * - Q1bTester.java  → strips suffix → "Q1b"  → matches pattern ✅ → returns "q1b"
     * - Q3Tester.java   → strips suffix → "Q3"   → matches pattern ✅ → returns "q3"
     * - MyTester.java   → strips suffix → "My"   → no match         ❌ → returns null
     * - Tester.java     → strips suffix → ""     → no match         ❌ → returns null
     * 
     * WHY STRICT:
     * - Old regex used find() which matched substrings - "MyQ1aHelper" would match "q1a"
     * - New regex uses matches() on the stripped prefix for exact full-string match
     * - Prevents false positives from oddly named files
     * 
     * @param filename Tester filename (e.g. "Q1aTester.java")
     * @return Lowercase question ID (e.g. "q1a"), or null if not a valid tester filename
     */
    private String extractQuestionId(String filename) {
        // Must end with "Tester.java" (case-sensitive, matching the convention)
        if (!filename.endsWith("Tester.java")) {
            return null;
        }

        // Strip the "Tester.java" suffix to get the question ID prefix (e.g. "Q1a")
        String prefix = filename.substring(0, filename.length() - "Tester.java".length());

        // Prefix must be empty after stripping -> reject "Tester.java" alone
        if (prefix.isEmpty()) {
            return null;
        }

        // Strict full-string match: must be Q + digits + optional single lowercase letter
        // Accepts: Q1, Q2, Q3, Q1a, Q1b, Q2a, Q10, Q10a
        // Rejects: MyQ1a, Q1aaaaaa, q1a (wrong case), Q1ab (two letters)
        if (prefix.matches("^Q\\d+[a-z]?$")) {
            return prefix.toLowerCase();
        }

        return null;
    }
    
    /**
     * Validates that a filename follows the strict tester naming convention
     * 
     * CONVENTION:
     * - Must end with exactly "Tester.java"
     * - Prefix must match ^Q\d+[a-z]?$ pattern
     * 
     * EXAMPLES:
     * - Q1aTester.java  ✅ valid
     * - Q3Tester.java   ✅ valid
     * - Tester.java     ❌ no prefix
     * - Q1a.java        ❌ doesn't end with Tester.java
     * - MyTester.java   ❌ prefix "My" doesn't match Q pattern
     * 
     * @param filename Filename to validate
     * @return true if valid tester filename, false otherwise
     */
    public boolean isValidTesterFilename(String filename) {
        return extractQuestionId(filename) != null;
    }

    
}