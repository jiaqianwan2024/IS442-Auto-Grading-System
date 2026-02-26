package com.autogradingsystem.penalty.strategies;

import com.autogradingsystem.penalty.GradingResult;

/**
 * Penalty strategy for structural violations.
 * 
 * Applies a 10% deduction of maxPossibleScore if ANY of the following are false:
 * - isNamingCorrect: Folder/zip doesn't match Email ID format
 * - hasProperHierarchy: Q1, Q2, Q3 folders not correctly placed
 * - hasHeaders: Name/Email not in code comments
 * 
 * This represents deductions for structural/organizational issues in the submission.
 */
public class StructuralPenalty implements PenaltyStrategy {
    private static final double DEDUCTION_PERCENTAGE = 0.10;

    @Override
    public double calculateDeduction(GradingResult result) {
        // Check if any structural requirement is violated
        boolean hasStructuralViolation = !result.isNamingCorrect() ||
                                         !result.isHasProperHierarchy() ||
                                         !result.isHasHeaders();

        if (hasStructuralViolation) {
            return result.getMaxPossibleScore() * DEDUCTION_PERCENTAGE;
        }

        return 0.0;
    }
}
