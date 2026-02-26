package com.autogradingsystem.penalty;

import com.autogradingsystem.penalty.service.PenaltyCalculator;
import com.autogradingsystem.penalty.strategies.PenaltyStrategy;
import java.util.ArrayList;
import java.util.List;

/**
 * Service orchestrator for penalty calculations.
 * 
 * This is the main entry point for the penalty microservice. It:
 * 1. Maintains a list of penalty strategies
 * 2. Iterates through all strategies to calculate total deductions
 * 3. Ensures the final score never goes below 0
 * 4. Returns a ProcessedScore object
 * 
 * Design Principles:
 * - Single Responsibility Principle (SRP): Only calculates penalties; doesn't read files or run tests
 * - Open/Closed Principle (OCP): New strategies can be added without modifying this class
 * 
 * Example Usage:
 * <pre>
 * PenaltyService service = new PenaltyService();
 * service.registerStrategy(new StructuralPenalty());
 * service.registerStrategy(new CompilationPenalty());
 * 
 * GradingResult result = new GradingResult(18.0, 20.0, false, false, true, true);
 * ProcessedScore score = service.processPenalties(result);
 * System.out.println(score); // ProcessedScore{rawScore=18.0, totalDeduction=2.0, finalScore=16.0}
 * </pre>
 */
public class PenaltyService {
    private final List<PenaltyStrategy> strategies;

    /**
     * Constructs a PenaltyService with an empty strategy list.
     * Strategies must be registered using registerStrategy().
     */
    public PenaltyService() {
        this.strategies = new ArrayList<>();
    }

    /**
     * Registers a penalty strategy to be applied during penalty calculation.
     * Strategies are applied in the order they are registered.
     *
     * @param strategy The penalty strategy to register
     * @return This PenaltyService for method chaining
     */
    public PenaltyService registerStrategy(PenaltyStrategy strategy) {
        if (strategy != null) {
            this.strategies.add(strategy);
        }
        return this;
    }

    /**
     * Processes penalties for a given grading result.
     * 
     * This method:
     * 1. Iterates through all registered strategies
     * 2. Calculates the deduction from each strategy
     * 3. Sums up the total deductions
     * 4. Ensures the final score doesn't go below 0
     * 5. Returns a ProcessedScore object
     *
     * @param result The grading result to process
     * @return A ProcessedScore containing the raw score, total deduction, and final score
     */
    public ProcessedScore processPenalties(GradingResult result) {
        if (result == null) {
            throw new IllegalArgumentException("GradingResult cannot be null");
        }

        double totalDeduction = 0.0;

        // Iterate through all strategies and accumulate deductions
        for (PenaltyStrategy strategy : strategies) {
            totalDeduction += strategy.calculateDeduction(result);
        }

        // Calculate final score, ensuring it never goes below 0
        double finalScore = Math.max(0.0, result.getRawScore() - totalDeduction);

        return new ProcessedScore(result.getRawScore(), totalDeduction, finalScore);
    }

    /**
     * Processes penalties for multiple questions using PenaltyCalculator's automatic rules,
     * then applies global CSV penalties at the end.
     *
     * Flow:
     * 1. For each question result, apply automatic penalties:
     *    - structural error: 20%
     *    - header error: 20%
     *    - compilation error: 50%
     * 2. Sum all question subtotals
     * 3. Apply global deductions from the CSV for the given student
     * 4. Return ProcessedScore for the overall submission
     *
     * @param studentId The student ID used to match CSV records
     * @param questionResults Per-question grading results
     * @param penaltiesCsvPath Path to penalties CSV file
     * @return A ProcessedScore containing total raw score, total deductions, and final score
     */
    public ProcessedScore processPenaltiesWithGlobalDeductions(
            String studentId,
            List<GradingResult> questionResults,
            String penaltiesCsvPath) {
        if (studentId == null || studentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Student ID cannot be null or empty");
        }
        if (questionResults == null || questionResults.isEmpty()) {
            throw new IllegalArgumentException("Question results cannot be null or empty");
        }
        if (penaltiesCsvPath == null || penaltiesCsvPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Penalties CSV path cannot be null or empty");
        }

        PenaltyCalculator calculator = new PenaltyCalculator();
        calculator.loadExternalPenalties(penaltiesCsvPath);

        double totalRawScore = 0.0;
        double totalAfterQuestionPenalties = 0.0;

        for (int i = 0; i < questionResults.size(); i++) {
            GradingResult questionResult = questionResults.get(i);
            if (questionResult == null) {
                throw new IllegalArgumentException("Question result at index " + i + " cannot be null");
            }

            totalRawScore += questionResult.getRawScore();

            String questionName = "Q" + (i + 1);
            boolean hasStructuralError = !questionResult.isHasProperHierarchy();
            boolean hasHeaderError = !questionResult.isHasHeaders();
            boolean hasCompilationError = questionResult.isHasCompilationError();

            totalAfterQuestionPenalties += calculator.calculateQuestionScore(
                    questionName,
                    questionResult.getRawScore(),
                    hasStructuralError,
                    hasHeaderError,
                    hasCompilationError);
        }

        double finalScore = calculator.calculateFinalTotal(studentId, totalAfterQuestionPenalties);
        double totalDeduction = totalRawScore - finalScore;

        return new ProcessedScore(totalRawScore, totalDeduction, finalScore);
    }

    /**
     * Gets the number of registered strategies.
     *
     * @return The count of registered strategies
     */
    public int getStrategyCount() {
        return strategies.size();
    }
}
