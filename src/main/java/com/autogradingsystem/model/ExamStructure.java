package com.autogradingsystem.model;

import java.util.*;

/**
 * ExamStructure - Data Model for Discovered Exam Structure
 * 
 * PURPOSE:
 * - Represents the structure of an exam discovered from template ZIP
 * - Stores questions (Q1, Q2, Q3) and their associated files
 * - Provides methods to query exam structure
 * 
 * DESIGN PATTERN: Value Object / Data Transfer Object (DTO)
 * - Immutable after construction (all fields final)
 * - No business logic - just data storage
 * - Easy to serialize/deserialize
 * - Thread-safe (immutable)
 * 
 * EXAMPLE DATA:
 * ExamStructure {
 *   questions: ["Q1", "Q2", "Q3"]
 *   questionFiles: {
 *     "Q1": ["Q1a.java", "Q1b.java"],
 *     "Q2": ["Q2a.java", "Q2b.java"],
 *     "Q3": ["Q3.java"]
 *   }
 * }
 * 
 * USAGE:
 * ExamStructure structure = templateDiscovery.discoverStructure(zipPath);
 * List<String> questions = structure.getQuestions();  // [Q1, Q2, Q3]
 * List<String> q1Files = structure.getQuestionFiles("Q1");  // [Q1a.java, Q1b.java]
 * 
 * @author IS442 Team
 * @version 1.0
 */
public class ExamStructure {
    
    // List of question IDs in order (Q1, Q2, Q3, ...)
    private final List<String> questions;
    
    // Map of question ID → list of Java files
    // Example: "Q1" → ["Q1a.java", "Q1b.java"]
    private final Map<String, List<String>> questionFiles;
    
    /**
     * Constructor - Creates immutable ExamStructure
     * 
     * IMMUTABILITY:
     * - Makes defensive copies of input collections
     * - Prevents external modification after creation
     * - Thread-safe
     * 
     * WHY DEFENSIVE COPIES:
     * Without defensive copy:
     *   List<String> myList = new ArrayList<>();
     *   ExamStructure s = new ExamStructure(myList, ...);
     *   myList.add("Q4");  // ❌ Would modify structure!
     * 
     * With defensive copy:
     *   List<String> myList = new ArrayList<>();
     *   ExamStructure s = new ExamStructure(myList, ...);
     *   myList.add("Q4");  // ✅ Doesn't affect structure
     * 
     * @param questions List of question IDs (will be copied)
     * @param questionFiles Map of question → files (will be copied)
     * @throws IllegalArgumentException if questions or questionFiles is null
     */
    public ExamStructure(List<String> questions, Map<String, List<String>> questionFiles) {
        
        // Input validation
        if (questions == null) {
            throw new IllegalArgumentException("Questions list cannot be null");
        }
        if (questionFiles == null) {
            throw new IllegalArgumentException("Question files map cannot be null");
        }
        
        // Defensive copy of questions list
        this.questions = new ArrayList<>(questions);
        
        // Defensive copy of questionFiles map
        // Need to copy both the map AND each list inside
        this.questionFiles = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : questionFiles.entrySet()) {
            this.questionFiles.put(
                entry.getKey(),
                new ArrayList<>(entry.getValue())  // Copy the list
            );
        }
    }
    
    /**
     * Returns list of all question IDs.
     * 
     * IMMUTABILITY:
     * - Returns unmodifiable list
     * - Caller cannot modify the structure
     * 
     * EXAMPLE:
     * List<String> questions = structure.getQuestions();
     * // Returns: ["Q1", "Q2", "Q3"]
     * 
     * @return Unmodifiable list of question IDs
     */
    public List<String> getQuestions() {
        return Collections.unmodifiableList(questions);
    }
    
    /**
     * Returns list of files for a specific question.
     * 
     * IMMUTABILITY:
     * - Returns unmodifiable list
     * - Returns empty list if question not found (not null!)
     * 
     * EXAMPLE:
     * List<String> q1Files = structure.getQuestionFiles("Q1");
     * // Returns: ["Q1a.java", "Q1b.java"]
     * 
     * List<String> q99Files = structure.getQuestionFiles("Q99");
     * // Returns: [] (empty list, not null)
     * 
     * @param questionId Question ID (e.g., "Q1")
     * @return Unmodifiable list of filenames, or empty list if not found
     */
    public List<String> getQuestionFiles(String questionId) {
        List<String> files = questionFiles.get(questionId);
        
        if (files == null) {
            // Question not found - return empty list (not null)
            return Collections.emptyList();
        }
        
        return Collections.unmodifiableList(files);
    }
    
    /**
     * Returns total number of questions.
     * 
     * EXAMPLE:
     * int count = structure.getQuestionCount();
     * // Returns: 3 (for Q1, Q2, Q3)
     * 
     * @return Number of questions
     */
    public int getQuestionCount() {
        return questions.size();
    }
    
    /**
     * Returns total number of Java files across all questions.
     * 
     * EXAMPLE:
     * Q1: 2 files (Q1a, Q1b)
     * Q2: 2 files (Q2a, Q2b)
     * Q3: 1 file (Q3)
     * Total: 5 files
     * 
     * @return Total file count
     */
    public int getTotalFileCount() {
        return questionFiles.values().stream()
                .mapToInt(List::size)
                .sum();
    }
    
    /**
     * Checks if a question exists in the structure.
     * 
     * EXAMPLE:
     * boolean hasQ1 = structure.hasQuestion("Q1");  // true
     * boolean hasQ99 = structure.hasQuestion("Q99");  // false
     * 
     * @param questionId Question ID to check
     * @return true if question exists, false otherwise
     */
    public boolean hasQuestion(String questionId) {
        return questionFiles.containsKey(questionId);
    }
    
    /**
     * Checks if structure is empty (no questions).
     * 
     * EXAMPLE:
     * boolean empty = structure.isEmpty();
     * 
     * @return true if no questions, false otherwise
     */
    public boolean isEmpty() {
        return questions.isEmpty();
    }
    
    /**
     * Returns detailed string representation for debugging.
     * 
     * EXAMPLE OUTPUT:
     * ExamStructure{
     *   questions: [Q1, Q2, Q3]
     *   Q1: [Q1a.java, Q1b.java]
     *   Q2: [Q2a.java, Q2b.java]
     *   Q3: [Q3.java]
     * }
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ExamStructure{\n");
        sb.append("  questions: ").append(questions).append("\n");
        
        for (String questionId : questions) {
            List<String> files = questionFiles.get(questionId);
            sb.append("  ").append(questionId).append(": ").append(files).append("\n");
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Equality check based on content.
     * Two ExamStructures are equal if they have the same questions and files.
     * 
     * @param obj Object to compare
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ExamStructure other = (ExamStructure) obj;
        return questions.equals(other.questions) && 
               questionFiles.equals(other.questionFiles);
    }
    
    /**
     * Hash code based on content.
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(questions, questionFiles);
    }
}