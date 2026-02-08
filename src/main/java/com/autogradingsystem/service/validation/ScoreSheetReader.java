package com.autogradingsystem.service.validation;

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
 * DATA STRUCTURE CHOICE:
 * - Uses HashSet for O(1) lookup time
 * - Alternative (ArrayList): Would be O(n) lookup - slow for large classes
 * - For 100 students: HashSet = 1 operation, ArrayList = up to 100 operations
 * 
 * CSV FORMAT EXPECTED:
 * Line 1 (header): OrgDefinedId,Username,Last Name,First Name,Email,...
 * Line 2+: #01400001,#ping.lee.2023,_,PING LEE,ping.lee.2023@...
 * 
 * @author IS442 Team (Original) + Enhancements
 * @version 2.0 (Updated to use Path API)
 */
public class ScoreSheetReader {
    
    // HashSet provides O(1) "contains" checks - very fast!
    // Stores clean usernames (without # prefix) for easy comparison
    private Set<String> validUsernames = new HashSet<>();
    
    /**
     * Reads the LMS CSV file and loads all valid student usernames.
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
     * CROSS-PLATFORM NOTES:
     * - Uses Path instead of String for file path
     * - Files.newBufferedReader(Path) works on all platforms
     * - Handles different line endings automatically:
     *   * Windows: \r\n (CRLF)
     *   * Mac/Linux: \n (LF)
     *   * Java's BufferedReader handles both!
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
        
        // Clear any previously loaded data (in case this method is called twice)
        validUsernames.clear();
        
        // Open CSV file with try-with-resources (auto-closes file)
        // Files.newBufferedReader() is the modern Java way (replaces FileReader)
        try (var reader = Files.newBufferedReader(csvPath)) {
            
            String line;
            
            // STEP 1: Skip the header row
            // First line is: "OrgDefinedId,Username,Last Name,..."
            // We don't need it, so read and discard
            reader.readLine();
            
            // STEP 2: Process each data row
            while ((line = reader.readLine()) != null) {
                
                // STEP 2a: Split line by comma
                // Example line: "#01400001,#ping.lee.2023,_,PING LEE,ping.lee.2023@..."
                // Result: ["#01400001", "#ping.lee.2023", "_", "PING LEE", "ping.lee.2023@..."]
                String[] columns = line.split(",");
                
                // STEP 2b: Safety check - ensure row has enough columns
                // Malformed CSV might have empty rows or rows with fewer columns
                // We need at least 2 columns (index 0 and index 1)
                if (columns.length > 1) {
                    
                    // STEP 2c: Extract username (column 2, which is index 1)
                    String rawUsername = columns[1];
                    
                    // STEP 2d: Clean the username
                    // Remove # prefix: "#ping.lee.2023" → "ping.lee.2023"
                    // Trim whitespace: " ping.lee.2023 " → "ping.lee.2023"
                    String cleanUsername = rawUsername.replace("#", "").trim();
                    
                    // STEP 2e: Add to HashSet
                    // If duplicate exists, HashSet automatically ignores it (no error)
                    validUsernames.add(cleanUsername);
                }
            }
        }
        // File automatically closed here by try-with-resources
        
        // Optional: Log how many students were loaded (helpful for debugging)
        System.out.println("   📋 Loaded " + validUsernames.size() + " valid students from CSV");
    }
    
    /**
     * Checks if a username exists in the official student list.
     * 
     * PERFORMANCE:
     * - HashSet.contains() is O(1) - constant time
     * - Doesn't matter if we have 10 or 10,000 students - always fast!
     * 
     * USAGE EXAMPLES:
     * - isValid("ping.lee.2023") → true (if in CSV)
     * - isValid("fake.student.2023") → false (not in CSV)
     * - isValid("PING.LEE.2023") → false (case-sensitive!)
     * 
     * CASE SENSITIVITY:
     * - This method is case-sensitive
     * - CSV has: "ping.lee.2023"
     * - Input must match exactly: "ping.lee.2023" ✅
     * - Won't match: "Ping.Lee.2023" ❌
     * 
     * DESIGN DECISION:
     * - Student IDs in SMU are lowercase
     * - If you need case-insensitive matching, convert both to lowercase:
     *   validUsernames.add(cleanUsername.toLowerCase());
     *   return validUsernames.contains(username.toLowerCase());
     * 
     * @param username The username to validate (without # prefix)
     * @return true if username exists in the loaded student list, false otherwise
     */
    public boolean isValid(String username) {
        return validUsernames.contains(username);
    }
    
    /**
     * Returns the number of valid students loaded.
     * Useful for verification and logging.
     * 
     * @return Number of students in the validation set
     */
    public int getStudentCount() {
        return validUsernames.size();
    }
    
    /**
     * Checks if any students have been loaded.
     * Useful to verify loadValidStudents() was called before validation.
     * 
     * @return true if student list is empty, false otherwise
     */
    public boolean isEmpty() {
        return validUsernames.isEmpty();
    }
}