package com.autogradingsystem.penalty;

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
     * Gets the number of registered strategies.
     *
     * @return The count of registered strategies
     */
    public int getStrategyCount() {
        return strategies.size();
    }
}
