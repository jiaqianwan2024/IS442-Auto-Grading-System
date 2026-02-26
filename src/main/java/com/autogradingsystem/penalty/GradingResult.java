package com.autogradingsystem.penalty;

/**
 * Data Transfer Object (DTO) representing the grading result passed from the Grading Service.
 * This is the contract between the Grading Service and the Penalty Service.
 */
public class GradingResult {
    private double rawScore;
    private double maxPossibleScore;
    private boolean hasCompilationError;
    private boolean isNamingCorrect;
    private boolean hasProperHierarchy;
    private boolean hasHeaders;

    /**
     * Constructs a GradingResult with all necessary grading criteria.
     *
     * @param rawScore             The score before penalties
     * @param maxPossibleScore     The total marks available (e.g., 20 or 100)
     * @param hasCompilationError  True if any .java file failed to compile
     * @param isNamingCorrect      True if the zip/folder matches the Email ID format
     * @param hasProperHierarchy   True if Q1, Q2, Q3 folders are correctly placed
     * @param hasHeaders           True if Name/Email are in the code comments
     */
    public GradingResult(double rawScore, double maxPossibleScore, boolean hasCompilationError,
                         boolean isNamingCorrect, boolean hasProperHierarchy, boolean hasHeaders) {
        this.rawScore = rawScore;
        this.maxPossibleScore = maxPossibleScore;
        this.hasCompilationError = hasCompilationError;
        this.isNamingCorrect = isNamingCorrect;
        this.hasProperHierarchy = hasProperHierarchy;
        this.hasHeaders = hasHeaders;
    }

    // Getters
    public double getRawScore() {
        return rawScore;
    }

    public double getMaxPossibleScore() {
        return maxPossibleScore;
    }

    public boolean isHasCompilationError() {
        return hasCompilationError;
    }

    public boolean isNamingCorrect() {
        return isNamingCorrect;
    }

    public boolean isHasProperHierarchy() {
        return hasProperHierarchy;
    }

    public boolean isHasHeaders() {
        return hasHeaders;
    }

    @Override
    public String toString() {
        return "GradingResult{" +
                "rawScore=" + rawScore +
                ", maxPossibleScore=" + maxPossibleScore +
                ", hasCompilationError=" + hasCompilationError +
                ", isNamingCorrect=" + isNamingCorrect +
                ", hasProperHierarchy=" + hasProperHierarchy +
                ", hasHeaders=" + hasHeaders +
                '}';
    }
}
