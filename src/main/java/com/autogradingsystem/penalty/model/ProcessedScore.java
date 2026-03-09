package com.autogradingsystem.penalty.model;

/**
 * Represents score details after all penalties are applied.
 */
public class ProcessedScore {
    private final double rawScore;
    private final double totalDeduction;
    private final double finalScore;

    public ProcessedScore(double rawScore, double totalDeduction, double finalScore) {
        this.rawScore = rawScore;
        this.totalDeduction = totalDeduction;
        this.finalScore = finalScore;
    }

    public double getRawScore() {
        return rawScore;
    }

    public double getTotalDeduction() {
        return totalDeduction;
    }

    public double getFinalScore() {
        return finalScore;
    }

    @Override
    public String toString() {
        return "ProcessedScore{" +
                "rawScore=" + rawScore +
                ", totalDeduction=" + totalDeduction +
                ", finalScore=" + finalScore +
                '}';
    }
}
