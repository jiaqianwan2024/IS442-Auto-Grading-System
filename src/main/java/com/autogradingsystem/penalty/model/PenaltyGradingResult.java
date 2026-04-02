package com.autogradingsystem.penalty.model;

/**
 * DTO representing grading data consumed by the penalty module.
 */
public class PenaltyGradingResult {
    private final String questionId;
    private final double rawScore;
    private final double maxPossibleScore;
    private final boolean rootFolderCorrect;
    private final boolean properHierarchy;
    private final boolean hasHeaders;
    private final boolean hasWrongPackage;

    public PenaltyGradingResult(String questionId,
                                double rawScore,
                                double maxPossibleScore,
                                boolean rootFolderCorrect,
                                boolean properHierarchy,
                                boolean hasHeaders,
                                boolean hasWrongPackage) {
        this.questionId = questionId;
        this.rawScore = rawScore;
        this.maxPossibleScore = maxPossibleScore;
        this.rootFolderCorrect = rootFolderCorrect;
        this.properHierarchy = properHierarchy;
        this.hasHeaders = hasHeaders;
        this.hasWrongPackage = hasWrongPackage;
    }

    public PenaltyGradingResult(double rawScore,
                                double maxPossibleScore,
                                boolean hasCompilationError,
                                boolean namingCorrect,
                                boolean properHierarchy,
                                boolean hasHeaders) {
        this(null, rawScore, maxPossibleScore, namingCorrect, properHierarchy, hasHeaders, false);
    }

    public String getQuestionId() {
        return questionId;
    }

    public double getRawScore() {
        return rawScore;
    }

    public double getMaxPossibleScore() {
        return maxPossibleScore;
    }

    public boolean isRootFolderCorrect() {
        return rootFolderCorrect;
    }

    public boolean hasProperHierarchy() {
        return properHierarchy;
    }

    public boolean hasHeaders() {
        return hasHeaders;
    }

    public boolean hasWrongPackage() {
        return hasWrongPackage;
    }
}
