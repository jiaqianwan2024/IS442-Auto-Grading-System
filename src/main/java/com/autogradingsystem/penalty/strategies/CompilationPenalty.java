package com.autogradingsystem.penalty.strategies;

import com.autogradingsystem.penalty.model.PenaltyGradingResult;

/**
 * Penalty strategy for compilation failures.
 * 
 * Applies a 50% deduction of maxPossibleScore if hasCompilationError is true.
 * This represents the severe penalty for code that fails to compile.
 * 
 * This is the most punitive penalty as compilation errors are critical issues
 * that prevent code from running.
 */
public class CompilationPenalty implements PenaltyStrategy {
    private static final double DEDUCTION_PERCENTAGE = 0.50;

    @Override
    public double calculateDeduction(PenaltyGradingResult result) {
        return 0.0;
    }
}
