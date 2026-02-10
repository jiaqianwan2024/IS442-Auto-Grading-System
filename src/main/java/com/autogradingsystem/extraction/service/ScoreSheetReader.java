package com.autogradingsystem.extraction.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * ScoreSheetReader - Validates student IDs against official LMS scoresheet
 * 
 * PURPOSE:
 * - Reads the official student list from IS442-ScoreSheet.csv
 * - Provides fast validation: "Is this student ID in the official class list?"
 * - Prevents grading submissions from non-enrolled students or fake IDs
 * 
 * USAGE PATTERN:
 * 1. Create instance: ScoreSheetReader reader = new ScoreSheetReader();
 * 2. Load CSV once: reader.loadValidStudents(csvPath);
 * 3. Validate many times: reader.isValid("ping.lee.2023");
 * 
 * DATA STRUCTURE:
 * - Uses HashSet for O(1) lookup time
 * - For 100 students: HashSet = 1 operation, ArrayList = up to 100 operations
 * 
 * CHANGES FROM v3.0:
 * - Removed verbose logging (handled by ExtractionController)
 * - Paths passed as parameters (no hardcoded paths)
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class ScoreSheetReader {
    
    // HashSet provides O(1) "contains" checks
    // Stores clean usernames (without # prefix) for easy comparison
    private Set<String> validUsernames = new HashSet<>();
    
    /**
     * Reads the LMS CSV file and loads all valid student usernames
     * 
     * WORKFLOW:
     * 1. Open CSV file
     * 2. Skip header row (column names)
     * 3. For each data row:
     *    a. Split by comma to get columns
     *    b. Extract username from column 2 (index 1)
     *    c. Clean it (remove # prefix)
     *    d. Add to HashSet
     * 
     * CSV COLUMN MAPPING:
     * Column 0: OrgDefinedId (e.g., #01400001)
     * Column 1: Username (e.g., #ping.lee.2023) ← WE EXTRACT THIS
     * Column 2: Last Name
     * Column 3: First Name
     * Column 4: Email
     * 
     * WHY REMOVE # PREFIX?
     * - LMS adds # to prevent Excel from treating IDs as formulas
     * - Our system uses plain usernames: "ping.lee.2023"
     * - Student ZIP filenames don't have #: "2023-2024-ping.lee.2023.zip"
     * - So we store cleaned version for easy matching
     * 
     * @param csvPath Path to the IS442-ScoreSheet.csv file
     * @throws IOException if file cannot be read or doesn't exist
     */
    public void loadValidStudents(Path csvPath) throws IOException {
        
        // Clear any previously loaded data
        validUsernames.clear();
        
        // Open CSV file with try-with-resources (auto-closes file)
        try (var reader = Files.newBufferedReader(csvPath)) {
            
            String line;
            
            // Skip the header row
            reader.readLine();
            
            // Process each data row
            while ((line = reader.readLine()) != null) {
                
                // Split line by comma
                String[] columns = line.split(",");
                
                // Safety check - ensure row has enough columns
                if (columns.length > 1) {
                    
                    // Extract username (column 2, which is index 1)
                    String rawUsername = columns[1];
                    
                    // Clean the username: remove # prefix and trim whitespace
                    String cleanUsername = rawUsername.replace("#", "").trim();
                    
                    // Add to HashSet
                    validUsernames.add(cleanUsername);
                }
            }
        }
    }
    
    /**
     * Checks if a username exists in the official student list
     * 
     * PERFORMANCE:
     * - HashSet.contains() is O(1) - constant time
     * - Doesn't matter if we have 10 or 10,000 students - always fast!
     * 
     * CASE SENSITIVITY:
     * - This method is case-sensitive
     * - CSV has: "ping.lee.2023"
     * - Input must match exactly: "ping.lee.2023" ✅
     * - Won't match: "Ping.Lee.2023" ❌
     * 
     * @param username The username to validate (without # prefix)
     * @return true if username exists in the loaded student list, false otherwise
     */
    public boolean isValid(String username) {
        return validUsernames.contains(username);
    }
    
    /**
     * Returns the number of valid students loaded
     * Useful for verification and logging
     * 
     * @return Number of students in the validation set
     */
    public int getStudentCount() {
        return validUsernames.size();
    }
    
    /**
     * Checks if any students have been loaded
     * Useful to verify loadValidStudents() was called before validation
     * 
     * @return true if student list is empty, false otherwise
     */
    public boolean isEmpty() {
        return validUsernames.isEmpty();
    }
}