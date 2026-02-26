package com.autogradingsystem.penalty.strategies;

import com.autogradingsystem.penalty.GradingResult;

/**
 * Strategy interface for penalty calculations.
 * Implements the Strategy Pattern to allow flexible penalty calculation logic.
 * 
 * New penalty strategies can be added by implementing this interface without
 * modifying the PenaltyService, adhering to the Open/Closed Principle.
 */
public interface PenaltyStrategy {
    /**
     * Calculates the penalty deduction based on the grading result.
     *
     * @param result The GradingResult containing information about the submission
     * @return The deduction amount (should be non-negative)
     */
    double calculateDeduction(GradingResult result);
}
