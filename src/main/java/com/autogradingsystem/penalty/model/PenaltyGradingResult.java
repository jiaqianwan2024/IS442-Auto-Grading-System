package com.autogradingsystem.penalty.model;

/**
 * DTO representing grading data consumed by the penalty module.
 */
public class PenaltyGradingResult {
    private final double rawScore;
    private final double maxPossibleScore;
    private final boolean hasCompilationError;
    private final boolean namingCorrect;
    private final boolean properHierarchy;
    private final boolean hasHeaders;

    public PenaltyGradingResult(double rawScore,
                                double maxPossibleScore,
                                boolean hasCompilationError,
                                boolean namingCorrect,
                                boolean properHierarchy,
                                boolean hasHeaders) {
        this.rawScore = rawScore;
        this.maxPossibleScore = maxPossibleScore;
        this.hasCompilationError = hasCompilationError;
        this.namingCorrect = namingCorrect;
        this.properHierarchy = properHierarchy;
        this.hasHeaders = hasHeaders;
    }

    public double getRawScore() {
        return rawScore;
    }

    public double getMaxPossibleScore() {
        return maxPossibleScore;
    }

    public boolean hasCompilationError() {
        return hasCompilationError;
    }

    public boolean isNamingCorrect() {
        return namingCorrect;
    }

    public boolean hasProperHierarchy() {
        return properHierarchy;
    }

    public boolean hasHeaders() {
        return hasHeaders;
    }
}
