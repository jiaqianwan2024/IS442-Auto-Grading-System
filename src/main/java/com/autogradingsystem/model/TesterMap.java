package com.autogradingsystem.model;

import java.util.*;

/**
 * TesterMap - Data Model for Tester File Mappings
 * 
 * PURPOSE:
 * - Maps question IDs to their corresponding tester files
 * - Provides lookup methods to find testers
 * - Handles missing testers gracefully
 * 
 * DESIGN PATTERN: Value Object / Data Transfer Object (DTO)
 * - Immutable after construction (all fields final)
 * - No business logic - just data storage and lookup
 * - Thread-safe (immutable)
 * 
 * EXAMPLE DATA:
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
 * USAGE:
 * TesterMap testers = testerDiscovery.discoverTesters(testersDir);
 * 
 * if (testers.hasTester("Q1a")) {
 *     String testerFile = testers.getTester("Q1a");  // "Q1aTester.java"
 *     // Use tester file...
 * }
 * 
 * @author IS442 Team
 * @version 1.0
 */
public class TesterMap {
    
    // Map of question ID → tester filename
    // Example: "Q1a" → "Q1aTester.java"
    private final Map<String, String> testers;
    
    /**
     * Constructor - Creates immutable TesterMap
     * 
     * IMMUTABILITY:
     * - Makes defensive copy of input map
     * - Prevents external modification after creation
     * - Thread-safe
     * 
     * @param testers Map of question ID → tester filename (will be copied)
     * @throws IllegalArgumentException if testers is null
     */
    public TesterMap(Map<String, String> testers) {
        
        // Input validation
        if (testers == null) {
            throw new IllegalArgumentException("Testers map cannot be null");
        }
        
        // Defensive copy of map
        this.testers = new HashMap<>(testers);
    }
    
    /**
     * Gets tester filename for a question ID.
     * 
     * RETURNS NULL IF NOT FOUND:
     * - This is intentional - allows caller to handle missing testers
     * - GradingPlanBuilder checks for null and logs warning
     * 
     * EXAMPLE:
     * String tester = map.getTester("Q1a");
     * // Returns: "Q1aTester.java" if exists
     * // Returns: null if not found
     * 
     * @param questionId Question ID (e.g., "Q1a")
     * @return Tester filename or null if not found
     */
    public String getTester(String questionId) {
        return testers.get(questionId);
    }
    
    /**
     * Checks if a tester exists for a question ID.
     * 
     * RECOMMENDED USAGE:
     * Always check hasTester() before getTester() if you want to avoid nulls
     * 
     * EXAMPLE:
     * if (map.hasTester("Q1a")) {
     *     String tester = map.getTester("Q1a");
     *     // Safe - tester is not null
     * } else {
     *     // Handle missing tester
     * }
     * 
     * @param questionId Question ID (e.g., "Q1a")
     * @return true if tester exists, false otherwise
     */
    public boolean hasTester(String questionId) {
        return testers.containsKey(questionId);
    }
    
    /**
     * Returns all question IDs that have testers.
     * 
     * IMMUTABILITY:
     * - Returns unmodifiable set
     * - Caller cannot modify the map
     * 
     * EXAMPLE:
     * Set<String> questionIds = map.getQuestionIds();
     * // Returns: ["Q1a", "Q1b", "Q2a", "Q2b", "Q3"]
     * 
     * @return Unmodifiable set of question IDs
     */
    public Set<String> getQuestionIds() {
        return Collections.unmodifiableSet(testers.keySet());
    }
    
    /**
     * Returns total number of testers.
     * 
     * EXAMPLE:
     * int count = map.getTesterCount();
     * // Returns: 5 (if we have 5 tester files)
     * 
     * @return Number of testers
     */
    public int getTesterCount() {
        return testers.size();
    }
    
    /**
     * Checks if map is empty (no testers).
     * 
     * EXAMPLE:
     * boolean empty = map.isEmpty();
     * 
     * @return true if no testers, false otherwise
     */
    public boolean isEmpty() {
        return testers.isEmpty();
    }
    
    /**
     * Returns all tester filenames.
     * Useful for validation or reporting.
     * 
     * EXAMPLE:
     * Collection<String> allTesters = map.getAllTesters();
     * // Returns: ["Q1aTester.java", "Q1bTester.java", ...]
     * 
     * @return Unmodifiable collection of tester filenames
     */
    public Collection<String> getAllTesters() {
        return Collections.unmodifiableCollection(testers.values());
    }
    
    /**
     * Returns detailed string representation for debugging.
     * 
     * EXAMPLE OUTPUT:
     * TesterMap{
     *   Q1a → Q1aTester.java
     *   Q1b → Q1bTester.java
     *   Q2a → Q2aTester.java
     *   Q2b → Q2bTester.java
     *   Q3 → Q3Tester.java
     *   Total: 5 testers
     * }
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TesterMap{\n");
        
        // Sort by question ID for consistent output
        List<String> sortedKeys = new ArrayList<>(testers.keySet());
        Collections.sort(sortedKeys);
        
        for (String questionId : sortedKeys) {
            sb.append("  ").append(questionId).append(" → ").append(testers.get(questionId)).append("\n");
        }
        
        sb.append("  Total: ").append(testers.size()).append(" tester(s)\n");
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Equality check based on content.
     * Two TesterMaps are equal if they have the same mappings.
     * 
     * @param obj Object to compare
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        TesterMap other = (TesterMap) obj;
        return testers.equals(other.testers);
    }
    
    /**
     * Hash code based on content.
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(testers);
    }
}