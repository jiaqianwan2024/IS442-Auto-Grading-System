package com.autogradingsystem.model;

import java.util.*;

/**
 * GradingPlan - Collection of All Grading Tasks
 * 
 * PURPOSE:
 * - Encapsulates the complete grading plan for an exam
 * - Contains all GradingTask objects to be executed
 * - Shared across discovery and execution services
 * - Immutable for thread safety
 * 
 * CREATED BY:
 * - GradingPlanBuilder during Phase 2 (Discovery)
 * 
 * USED BY:
 * - ExecutionController for executing grading
 * - Main.java for validation
 * 
 * NO CHANGES FROM v3.0:
 * - Already well-designed
 * - Package stays in global model/ (shared across services)
 * - Added comprehensive JavaDoc
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class GradingPlan {
    
    // ================================================================
    // FIELDS
    // ================================================================
    
    /**
     * List of all grading tasks
     * 
     * CONTAINS:
     * - One GradingTask per question file to grade
     * - Typically 5-10 tasks for a standard exam
     * 
     * EXAMPLE:
     * [
     *   GradingTask(Q1a, Q1aTester.java, Q1, Q1a.java),
     *   GradingTask(Q1b, Q1bTester.java, Q1, Q1b.java),
     *   GradingTask(Q2a, Q2aTester.java, Q2, Q2a.java),
     *   GradingTask(Q2b, Q2bTester.java, Q2, Q2b.java),
     *   GradingTask(Q3, Q3Tester.java, Q3, Q3.java)
     * ]
     * 
     * IMMUTABILITY:
     * - List is unmodifiable after construction
     * - Tasks themselves are immutable
     */
    private final List<GradingTask> tasks;
    
    // ================================================================
    // CONSTRUCTOR
    // ================================================================
    
    /**
     * Creates a GradingPlan from a list of tasks
     * 
     * DEFENSIVE COPYING:
     * - Makes unmodifiable copy of input list
     * - Prevents external modification after construction
     * - Ensures true immutability
     * 
     * @param tasks List of GradingTask objects
     */
    public GradingPlan(List<GradingTask> tasks) {
        // Create defensive unmodifiable copy
        this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
    }
    
    // ================================================================
    // GETTERS
    // ================================================================
    
    /**
     * Gets the complete list of grading tasks
     * 
     * RETURNS:
     * Unmodifiable list of all GradingTask objects
     * 
     * USED FOR:
     * - Iterating over tasks during grading
     * - Counting total tasks
     * - Validation
     * 
     * @return Unmodifiable list of all tasks
     */
    public List<GradingTask> getTasks() {
        return tasks;
    }
    
    /**
     * Gets total number of tasks in the plan
     * 
     * USEFUL FOR:
     * - Progress tracking ("Grading task 3 of 5")
     * - Validation (ensure plan is not empty)
     * - Logging
     * 
     * @return Number of tasks
     */
    public int getTaskCount() {
        return tasks.size();
    }
    
    // ================================================================
    // QUERY METHODS
    // ================================================================
    
    /**
     * Checks if plan is empty (no tasks to grade)
     * 
     * USEFUL FOR:
     * - Validation before starting grading
     * - Error handling
     * 
     * @return true if no tasks exist, false otherwise
     */
    public boolean isEmpty() {
        return tasks.isEmpty();
    }
    
    /**
     * Gets task by index
     * 
     * USEFUL FOR:
     * - Sequential processing
     * - Progress tracking with index
     * 
     * @param index Task index (0-based)
     * @return GradingTask at the specified index
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public GradingTask getTask(int index) {
        return tasks.get(index);
    }
    
    /**
     * Finds task by question ID
     * 
     * USEFUL FOR:
     * - Looking up specific question
     * - Checking if question exists in plan
     * 
     * EXAMPLE:
     * getTaskByQuestionId("Q1a") → GradingTask for Q1a
     * getTaskByQuestionId("Q99") → null (not in plan)
     * 
     * @param questionId Question ID to find
     * @return GradingTask for the question, or null if not found
     */
    public GradingTask getTaskByQuestionId(String questionId) {
        for (GradingTask task : tasks) {
            if (task.getQuestionId().equals(questionId)) {
                return task;
            }
        }
        return null;
    }
    
    /**
     * Checks if plan contains a task for the given question ID
     * 
     * @param questionId Question ID to check
     * @return true if task exists, false otherwise
     */
    public boolean hasTask(String questionId) {
        return getTaskByQuestionId(questionId) != null;
    }
    
    /**
     * Gets all unique question IDs in the plan
     * 
     * USEFUL FOR:
     * - Listing all graded questions
     * - Validation
     * - Reporting
     * 
     * @return List of all question IDs
     */
    public List<String> getQuestionIds() {
        List<String> questionIds = new ArrayList<>();
        for (GradingTask task : tasks) {
            questionIds.add(task.getQuestionId());
        }
        return questionIds;
    }
    
    /**
     * Gets all unique question folders in the plan
     * 
     * USEFUL FOR:
     * - Listing all question folders (Q1, Q2, Q3)
     * - Navigation
     * - Validation
     * 
     * @return Set of all question folder names
     */
    public Set<String> getQuestionFolders() {
        Set<String> folders = new HashSet<>();
        for (GradingTask task : tasks) {
            folders.add(task.getStudentFolder());
        }
        return folders;
    }
    
    // ================================================================
    // FILTERING METHODS
    // ================================================================
    
    /**
     * Gets all tasks for a specific question folder
     * 
     * USEFUL FOR:
     * - Grading all parts of Q1 together
     * - Folder-based organization
     * 
     * EXAMPLE:
     * getTasksForFolder("Q1") → [Q1a task, Q1b task]
     * 
     * @param questionFolder Folder name (e.g., "Q1")
     * @return List of tasks in that folder
     */
    public List<GradingTask> getTasksForFolder(String questionFolder) {
        List<GradingTask> folderTasks = new ArrayList<>();
        for (GradingTask task : tasks) {
            if (task.getStudentFolder().equals(questionFolder)) {
                folderTasks.add(task);
            }
        }
        return folderTasks;
    }
    
    // ================================================================
    // OBJECT METHODS
    // ================================================================
    
    /**
     * String representation for debugging
     * 
     * FORMAT:
     * GradingPlan{tasks=5, questions=[Q1a, Q1b, Q2a, Q2b, Q3]}
     * 
     * @return Human-readable representation
     */
    @Override
    public String toString() {
        return String.format(
            "GradingPlan{tasks=%d, questions=%s}",
            tasks.size(),
            getQuestionIds()
        );
    }
    
    /**
     * Checks equality based on tasks list
     * 
     * @param obj Object to compare with
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        GradingPlan other = (GradingPlan) obj;
        return tasks.equals(other.tasks);
    }
    
    /**
     * Generates hash code based on tasks list
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return tasks.hashCode();
    }
}