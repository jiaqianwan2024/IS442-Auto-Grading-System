package com.autogradingsystem.penalty;

/**
 * Represents the final score after all penalties have been applied.
 * This is the output of the PenaltyService.
 */
public class ProcessedScore {
    private double rawScore;
    private double totalDeduction;
    private double finalScore;

    /**
     * Constructs a ProcessedScore with the calculated values.
     *
     * @param rawScore        The original score before penalties
     * @param totalDeduction  The total amount deducted from penalties
     * @param finalScore      The final score after deductions (guaranteed >= 0)
     */
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
