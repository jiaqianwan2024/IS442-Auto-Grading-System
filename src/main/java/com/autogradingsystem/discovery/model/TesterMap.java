package com.autogradingsystem.discovery.model;

import java.util.*;

/**
 * TesterMap - Maps Question IDs to Tester Files
 * 
 * PURPOSE:
 * - Encapsulates the mapping from question IDs to tester filenames
 * - Provides convenient lookup methods
 * - Ensures immutability and type safety
 * 
 * MAPPING EXAMPLE:
 * {
 *   "Q1a": "Q1aTester.java",
 *   "Q1b": "Q1bTester.java",
 *   "Q2a": "Q2aTester.java",
 *   "Q2b": "Q2bTester.java",
 *   "Q3": "Q3Tester.java"
 * }
 * 
 * DESIGN:
 * - Immutable (defensive copy on construction)
 * - Service-specific model (lives in discovery.model)
 * - Created by TesterDiscovery, consumed by GradingPlanBuilder
 * 
 * CHANGES FROM v3.0:
 * - Moved from global model/ to discovery.model/
 * - Added comprehensive JavaDoc
 * - Added helper methods for convenience
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class TesterMap {
    
    // ================================================================
    // FIELDS
    // ================================================================
    
    /**
     * Maps question ID to tester filename
     * 
     * KEY: Question ID (e.g., "Q1a", "Q1b", "Q2a")
     * VALUE: Tester filename (e.g., "Q1aTester.java")
     * 
     * IMMUTABILITY:
     * - Map is unmodifiable after construction
     * - No way to add/remove entries after creation
     */
    private final Map<String, String> testerMapping;
    
    // ================================================================
    // CONSTRUCTOR
    // ================================================================
    
    /**
     * Creates TesterMap from question ID → tester filename mapping
     * 
     * DEFENSIVE COPYING:
     * - Makes copy of input map
     * - Prevents external modification after construction
     * - Ensures true immutability
     * 
     * @param testerMapping Map of question ID → tester filename
     */
    public TesterMap(Map<String, String> testerMapping) {
        // Create defensive unmodifiable copy
        this.testerMapping = Collections.unmodifiableMap(
            new HashMap<>(testerMapping)
        );
    }
    
    // ================================================================
    // GETTERS
    // ================================================================
    
    /**
     * Gets the complete tester mapping
     * 
     * RETURNS:
     * Unmodifiable map of question ID → tester filename
     * 
     * EXAMPLE:
     * {
     *   "Q1a": "Q1aTester.java",
     *   "Q1b": "Q1bTester.java",
     *   "Q2a": "Q2aTester.java"
     * }
     * 
     * @return Unmodifiable map of all tester mappings
     */
    public Map<String, String> getTesterMapping() {
        return testerMapping;
    }
    
    /**
     * Gets tester filename for a specific question ID
     * 
     * EXAMPLE:
     * getTesterForQuestion("Q1a") → "Q1aTester.java"
     * getTesterForQuestion("Q99") → null (no tester for Q99)
     * 
     * @param questionId Question ID to look up
     * @return Tester filename, or null if no tester exists for this question
     */
    public String getTesterForQuestion(String questionId) {
        return testerMapping.get(questionId);
    }
    
    /**
     * Checks if a tester exists for the given question ID
     * 
     * EXAMPLE:
     * hasTester("Q1a") → true
     * hasTester("Q99") → false
     * 
     * @param questionId Question ID to check
     * @return true if tester exists, false otherwise
     */
    public boolean hasTester(String questionId) {
        return testerMapping.containsKey(questionId);
    }
    
    /**
     * Gets set of all question IDs that have testers
     * 
     * USEFUL FOR:
     * - Iterating over all graded questions
     * - Checking coverage
     * - Finding missing testers
     * 
     * EXAMPLE:
     * ["Q1a", "Q1b", "Q2a", "Q2b", "Q3"]
     * 
     * @return Set of all question IDs with testers
     */
    public Set<String> getQuestionIds() {
        return testerMapping.keySet();
    }
    
    /**
     * Gets collection of all tester filenames
     * 
     * USEFUL FOR:
     * - Listing all testers
     * - Checking for duplicates
     * - Validation
     * 
     * EXAMPLE:
     * ["Q1aTester.java", "Q1bTester.java", "Q2aTester.java"]
     * 
     * @return Collection of all tester filenames
     */
    public Collection<String> getTesterFilenames() {
        return testerMapping.values();
    }
    
    /**
     * Gets total number of testers
     * 
     * @return Number of testers (e.g., 5 for Q1a, Q1b, Q2a, Q2b, Q3)
     */
    public int getTesterCount() {
        return testerMapping.size();
    }
    
    // ================================================================
    // VALIDATION METHODS
    // ================================================================
    
    /**
     * Checks if map is empty (no testers found)
     * 
     * @return true if no testers exist, false otherwise
     */
    public boolean isEmpty() {
        return testerMapping.isEmpty();
    }
    
    /**
     * Finds question IDs that exist in this map but not in the given set
     * 
     * USE CASE:
     * - Find testers that don't have corresponding question files
     * - Identify orphaned testers
     * 
     * EXAMPLE:
     * TesterMap has: [Q1a, Q1b, Q2a, Q99]
     * Questions are: [Q1a, Q1b, Q2a]
     * Returns: [Q99] (tester exists but no question file)
     * 
     * @param questionIds Set of question IDs that exist
     * @return Set of question IDs with testers but no question files
     */
    public Set<String> findOrphanedTesters(Set<String> questionIds) {
        Set<String> orphaned = new HashSet<>(this.testerMapping.keySet());
        orphaned.removeAll(questionIds);
        return orphaned;
    }
    
    /**
     * Finds question IDs in the given set that don't have testers
     * 
     * USE CASE:
     * - Find questions that are missing testers
     * - Identify coverage gaps
     * 
     * EXAMPLE:
     * TesterMap has: [Q1a, Q1b, Q2a]
     * Questions are: [Q1a, Q1b, Q2a, Q2b, Q3]
     * Returns: [Q2b, Q3] (questions exist but no testers)
     * 
     * @param questionIds Set of all question IDs
     * @return Set of question IDs without testers
     */
    public Set<String> findMissingTesters(Set<String> questionIds) {
        Set<String> missing = new HashSet<>(questionIds);
        missing.removeAll(this.testerMapping.keySet());
        return missing;
    }
    
    // ================================================================
    // OBJECT METHODS
    // ================================================================
    
    /**
     * String representation for debugging
     * 
     * FORMAT:
     * TesterMap{
     *   Q1a → Q1aTester.java
     *   Q1b → Q1bTester.java
     *   Q2a → Q2aTester.java
     * }
     * 
     * @return Human-readable representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TesterMap{\n");
        
        for (Map.Entry<String, String> entry : testerMapping.entrySet()) {
            sb.append("  ")
              .append(entry.getKey())
              .append(" → ")
              .append(entry.getValue())
              .append("\n");
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Checks equality based on testerMapping map
     * 
     * @param obj Object to compare with
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        TesterMap other = (TesterMap) obj;
        return testerMapping.equals(other.testerMapping);
    }
    
    /**
     * Generates hash code based on testerMapping map
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return testerMapping.hashCode();
    }
}