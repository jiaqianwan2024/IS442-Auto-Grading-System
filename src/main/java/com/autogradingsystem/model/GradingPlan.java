package com.autogradingsystem.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GradingPlan - Data Model for Complete Grading Plan
 * 
 * PURPOSE:
 * - Contains all grading tasks for an exam
 * - Provides methods to query and iterate over tasks
 * - Represents the complete grading strategy
 * 
 * DESIGN PATTERN: Value Object / Data Transfer Object (DTO)
 * - Immutable after construction (all fields final)
 * - No business logic - just data storage and queries
 * - Thread-safe (immutable)
 * 
 * EXAMPLE DATA:
 * GradingPlan {
 *   tasks: [
 *     GradingTask{Q1a, Q1aTester.java, Q1, Q1a.java},
 *     GradingTask{Q1b, Q1bTester.java, Q1, Q1b.java},
 *     GradingTask{Q2a, Q2aTester.java, Q2, Q2a.java},
 *     GradingTask{Q2b, Q2bTester.java, Q2, Q2b.java},
 *     GradingTask{Q3, Q3Tester.java, Q3, Q3.java}
 *   ]
 * }
 * 
 * USAGE IN MAIN GRADING LOOP:
 * GradingPlan plan = controller.initialize();
 * 
 * for (Student student : students) {
 *     for (GradingTask task : plan.getTasks()) {
 *         // Grade student on this task
 *         int score = gradeTask(student, task);
 *     }
 * }
 * 
 * @author IS442 Team
 * @version 1.0
 */
public class GradingPlan {
    
    // List of all grading tasks in order
    private final List<GradingTask> tasks;
    
    /**
     * Constructor - Creates immutable GradingPlan
     * 
     * IMMUTABILITY:
     * - Makes defensive copy of input list
     * - Prevents external modification after creation
     * - Thread-safe
     * 
     * @param tasks List of GradingTask objects (will be copied)
     * @throws IllegalArgumentException if tasks is null
     */
    public GradingPlan(List<GradingTask> tasks) {
        
        // Input validation
        if (tasks == null) {
            throw new IllegalArgumentException("Tasks list cannot be null");
        }
        
        // Defensive copy of list
        this.tasks = new ArrayList<>(tasks);
    }
    
    /**
     * Returns all grading tasks.
     * 
     * IMMUTABILITY:
     * - Returns unmodifiable list
     * - Caller cannot modify the plan
     * 
     * EXAMPLE:
     * List<GradingTask> tasks = plan.getTasks();
     * for (GradingTask task : tasks) {
     *     // Process task
     * }
     * 
     * @return Unmodifiable list of grading tasks
     */
    public List<GradingTask> getTasks() {
        return Collections.unmodifiableList(tasks);
    }
    
    /**
     * Returns total number of tasks.
     * 
     * EXAMPLE:
     * int count = plan.getTaskCount();
     * // Returns: 5 (for Q1a, Q1b, Q2a, Q2b, Q3)
     * 
     * @return Number of tasks
     */
    public int getTaskCount() {
        return tasks.size();
    }
    
    /**
     * Checks if plan is empty (no tasks).
     * 
     * EXAMPLE:
     * boolean empty = plan.isEmpty();
     * 
     * @return true if no tasks, false otherwise
     */
    public boolean isEmpty() {
        return tasks.isEmpty();
    }
    
    /**
     * Gets a specific task by index.
     * 
     * EXAMPLE:
     * GradingTask firstTask = plan.getTask(0);  // Q1a
     * 
     * @param index Task index (0-based)
     * @return GradingTask at index
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public GradingTask getTask(int index) {
        return tasks.get(index);
    }
    
    /**
     * Finds task by question ID.
     * 
     * EXAMPLE:
     * Optional<GradingTask> task = plan.findTaskByQuestionId("Q1a");
     * if (task.isPresent()) {
     *     // Use task
     * }
     * 
     * @param questionId Question ID to search for
     * @return Optional containing task if found, empty otherwise
     */
    public Optional<GradingTask> findTaskByQuestionId(String questionId) {
        return tasks.stream()
                .filter(task -> task.getQuestionId().equals(questionId))
                .findFirst();
    }
    
    /**
     * Returns tasks that have testers.
     * Useful for reporting how many tasks can be graded.
     * 
     * EXAMPLE:
     * List<GradingTask> gradableTasks = plan.getTasksWithTesters();
     * System.out.println(gradableTasks.size() + " out of " + 
     *                    plan.getTaskCount() + " tasks can be graded");
     * 
     * @return List of tasks with testers
     */
    public List<GradingTask> getTasksWithTesters() {
        return tasks.stream()
                .filter(GradingTask::hasTester)
                .collect(Collectors.toList());
    }
    
    /**
     * Returns tasks that are missing testers.
     * Useful for warning the instructor.
     * 
     * EXAMPLE:
     * List<GradingTask> missingTesters = plan.getTasksWithoutTesters();
     * if (!missingTesters.isEmpty()) {
     *     System.err.println("⚠️ Warning: " + missingTesters.size() + 
     *                        " task(s) missing testers");
     *     for (GradingTask task : missingTesters) {
     *         System.err.println("  - " + task.getQuestionId());
     *     }
     * }
     * 
     * @return List of tasks without testers
     */
    public List<GradingTask> getTasksWithoutTesters() {
        return tasks.stream()
                .filter(task -> !task.hasTester())
                .collect(Collectors.toList());
    }
    
    /**
     * Returns count of tasks with testers.
     * 
     * EXAMPLE:
     * int gradable = plan.getGradableTaskCount();
     * // Returns: 4 (if 4 out of 5 tasks have testers)
     * 
     * @return Number of tasks with testers
     */
    public int getGradableTaskCount() {
        return (int) tasks.stream()
                .filter(GradingTask::hasTester)
                .count();
    }
    
    /**
     * Returns count of tasks without testers.
     * 
     * EXAMPLE:
     * int ungradable = plan.getUngradableTaskCount();
     * // Returns: 1 (if 1 out of 5 tasks missing tester)
     * 
     * @return Number of tasks without testers
     */
    public int getUngradableTaskCount() {
        return (int) tasks.stream()
                .filter(task -> !task.hasTester())
                .count();
    }
    
    /**
     * Returns all unique question folders.
     * Useful for understanding exam structure.
     * 
     * EXAMPLE:
     * Set<String> folders = plan.getQuestionFolders();
     * // Returns: ["Q1", "Q2", "Q3"]
     * 
     * @return Set of unique folder names
     */
    public Set<String> getQuestionFolders() {
        return tasks.stream()
                .map(GradingTask::getStudentFolder)
                .collect(Collectors.toSet());
    }
    
    /**
     * Returns summary statistics for logging.
     * 
     * EXAMPLE OUTPUT:
     * "5 tasks (4 with testers, 1 missing tester)"
     * 
     * @return Summary string
     */
    public String getSummary() {
        int total = getTaskCount();
        int gradable = getGradableTaskCount();
        int ungradable = getUngradableTaskCount();
        
        StringBuilder summary = new StringBuilder();
        summary.append(total).append(" task").append(total == 1 ? "" : "s");
        
        if (ungradable > 0) {
            summary.append(" (")
                   .append(gradable).append(" with tester").append(gradable == 1 ? "" : "s")
                   .append(", ")
                   .append(ungradable).append(" missing tester").append(ungradable == 1 ? "" : "s")
                   .append(")");
        }
        
        return summary.toString();
    }
    
    /**
     * Returns detailed string representation for debugging.
     * 
     * EXAMPLE OUTPUT:
     * GradingPlan{
     *   tasks: 5
     *   [1] Q1a → Q1aTester.java
     *   [2] Q1b → Q1bTester.java
     *   [3] Q2a → Q2aTester.java
     *   [4] Q2b → Q2bTester.java
     *   [5] Q3 → Q3Tester.java
     *   Summary: 5 tasks (5 with testers, 0 missing testers)
     * }
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("GradingPlan{\n");
        sb.append("  tasks: ").append(tasks.size()).append("\n");
        
        for (int i = 0; i < tasks.size(); i++) {
            GradingTask task = tasks.get(i);
            sb.append("  [").append(i + 1).append("] ")
              .append(task.getQuestionId()).append(" → ");
            
            if (task.hasTester()) {
                sb.append(task.getTesterFile());
            } else {
                sb.append("NONE (missing tester)");
            }
            
            sb.append("\n");
        }
        
        sb.append("  Summary: ").append(getSummary()).append("\n");
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Equality check based on content.
     * Two GradingPlans are equal if they have the same tasks in the same order.
     * 
     * @param obj Object to compare
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
     * Hash code based on content.
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(tasks);
    }
}