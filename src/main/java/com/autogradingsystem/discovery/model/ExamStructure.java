package com.autogradingsystem.discovery.model;

import java.util.*;

/**
 * ExamStructure - Represents Discovered Exam Structure
 * 
 * PURPOSE:
 * - Encapsulates the discovered exam structure from template
 * - Maps question folders to their .java files
 * - Provides convenient access methods for structure information
 * 
 * STRUCTURE EXAMPLE:
 * {
 *   "Q1": ["Q1a.java", "Q1b.java"],
 *   "Q2": ["Q2a.java", "Q2b.java"],
 *   "Q3": ["Q3.java", "ShapeComparator.java"]
 * }
 * 
 * DESIGN:
 * - Immutable (defensive copy on construction)
 * - Service-specific model (lives in discovery.model)
 * - Created by TemplateDiscovery, consumed by GradingPlanBuilder
 * 
 * CHANGES FROM v3.0:
 * - Moved from global model/ to discovery.model/
 * - Added comprehensive JavaDoc
 * - Added helper methods for convenience
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class ExamStructure {
    
    // ================================================================
    // FIELDS
    // ================================================================
    
    /**
     * Maps question folder name to list of .java files in that folder
     * 
     * KEY: Question folder (e.g., "Q1", "Q2", "Q3")
     * VALUE: List of .java filenames (e.g., ["Q1a.java", "Q1b.java"])
     * 
     * IMMUTABILITY:
     * - Map itself is unmodifiable
     * - File lists are unmodifiable
     * - No way to modify after construction
     */
    private final Map<String, List<String>> questionFiles;
    
    // ================================================================
    // CONSTRUCTOR
    // ================================================================
    
    /**
     * Creates ExamStructure from question files map
     * 
     * DEFENSIVE COPYING:
     * - Makes deep copy of input map
     * - Prevents external modification after construction
     * - Ensures true immutability
     * 
     * @param questionFiles Map of question folder → file list
     */
    public ExamStructure(Map<String, List<String>> questionFiles) {
        
        // Create defensive copy with unmodifiable collections
        Map<String, List<String>> copy = new HashMap<>();
        
        for (Map.Entry<String, List<String>> entry : questionFiles.entrySet()) {
            // Make each file list unmodifiable
            List<String> unmodifiableList = Collections.unmodifiableList(
                new ArrayList<>(entry.getValue())
            );
            copy.put(entry.getKey(), unmodifiableList);
        }
        
        // Make the entire map unmodifiable
        this.questionFiles = Collections.unmodifiableMap(copy);
    }
    
    // ================================================================
    // GETTERS
    // ================================================================
    
    /**
     * Gets the complete question files map
     * 
     * RETURNS:
     * Unmodifiable map of question folder → file list
     * 
     * EXAMPLE:
     * {
     *   "Q1": ["Q1a.java", "Q1b.java"],
     *   "Q2": ["Q2a.java", "Q2b.java"]
     * }
     * 
     * @return Unmodifiable map of all question files
     */
    public Map<String, List<String>> getQuestionFiles() {
        return questionFiles;
    }
    
    /**
     * Gets set of all question folder names
     * 
     * USEFUL FOR:
     * - Iterating over questions
     * - Checking if specific question exists
     * - Counting total questions
     * 
     * EXAMPLE:
     * ["Q1", "Q2", "Q3"]
     * 
     * @return Set of question folder names
     */
    public Set<String> getQuestionFolders() {
        return questionFiles.keySet();
    }
    
    /**
     * Gets list of .java files for a specific question folder
     * 
     * EXAMPLE:
     * getFilesForQuestion("Q1") → ["Q1a.java", "Q1b.java"]
     * 
     * @param questionFolder Question folder name (e.g., "Q1")
     * @return List of .java files, or empty list if question doesn't exist
     */
    public List<String> getFilesForQuestion(String questionFolder) {
        return questionFiles.getOrDefault(questionFolder, Collections.emptyList());
    }
    
    /**
     * Checks if a question folder exists in the structure
     * 
     * @param questionFolder Question folder name to check
     * @return true if question exists, false otherwise
     */
    public boolean hasQuestion(String questionFolder) {
        return questionFiles.containsKey(questionFolder);
    }
    
    /**
     * Gets total number of question folders
     * 
     * @return Number of questions (e.g., 3 for Q1, Q2, Q3)
     */
    public int getQuestionCount() {
        return questionFiles.size();
    }
    
    /**
     * Gets total number of .java files across all questions
     * 
     * EXAMPLE:
     * Q1 has 2 files, Q2 has 2 files, Q3 has 2 files → returns 6
     * 
     * @return Total number of .java files
     */
    public int getTotalFileCount() {
        return questionFiles.values().stream()
            .mapToInt(List::size)
            .sum();
    }
    
    // ================================================================
    // VALIDATION METHODS
    // ================================================================
    
    /**
     * Checks if structure is empty (no questions found)
     * 
     * @return true if no questions exist, false otherwise
     */
    public boolean isEmpty() {
        return questionFiles.isEmpty();
    }
    
    /**
     * Validates that structure contains at least one file
     * 
     * CHECKS:
     * - At least one question folder exists
     * - At least one .java file exists
     * 
     * @return true if valid, false if empty or all folders empty
     */
    public boolean isValid() {
        if (questionFiles.isEmpty()) {
            return false;
        }
        
        // Check if any question has files
        return questionFiles.values().stream()
            .anyMatch(files -> !files.isEmpty());
    }
    
    // ================================================================
    // OBJECT METHODS
    // ================================================================
    
    /**
     * String representation for debugging
     * 
     * FORMAT:
     * ExamStructure{
     *   Q1: [Q1a.java, Q1b.java]
     *   Q2: [Q2a.java, Q2b.java]
     *   Q3: [Q3.java, ShapeComparator.java]
     * }
     * 
     * @return Human-readable representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ExamStructure{\n");
        
        for (Map.Entry<String, List<String>> entry : questionFiles.entrySet()) {
            sb.append("  ")
              .append(entry.getKey())
              .append(": ")
              .append(entry.getValue())
              .append("\n");
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Checks equality based on questionFiles map
     * 
     * @param obj Object to compare with
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ExamStructure other = (ExamStructure) obj;
        return questionFiles.equals(other.questionFiles);
    }
    
    /**
     * Generates hash code based on questionFiles map
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return questionFiles.hashCode();
    }
}