package com.autogradingsystem.penalty.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents score details after all penalties are applied.
 */
public class ProcessedScore {
    private final double rawScore;
    private final double totalDeduction;
    private final double finalScore;
    private final String penaltyRulesApplied;
    private final Map<String, Double> adjustedQuestionScores;

    public ProcessedScore(double rawScore, double totalDeduction, double finalScore) {
        this(rawScore, totalDeduction, finalScore, "", Collections.emptyMap());
    }

    public ProcessedScore(double rawScore, double totalDeduction, double finalScore, String penaltyRulesApplied) {
        this(rawScore, totalDeduction, finalScore, penaltyRulesApplied, Collections.emptyMap());
    }

    public ProcessedScore(double rawScore,
                          double totalDeduction,
                          double finalScore,
                          String penaltyRulesApplied,
                          Map<String, Double> adjustedQuestionScores) {
        this.rawScore = rawScore;
        this.totalDeduction = totalDeduction;
        this.finalScore = finalScore;
        this.penaltyRulesApplied = penaltyRulesApplied == null ? "" : penaltyRulesApplied;
        this.adjustedQuestionScores = adjustedQuestionScores == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(adjustedQuestionScores));
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

    public String getPenaltyRulesApplied() {
        return penaltyRulesApplied;
    }

    public Map<String, Double> getAdjustedQuestionScores() {
        return adjustedQuestionScores;
    }

    @Override
    public String toString() {
        return "ProcessedScore{" +
                "rawScore=" + rawScore +
                ", totalDeduction=" + totalDeduction +
                ", finalScore=" + finalScore +
                ", penaltyRulesApplied='" + penaltyRulesApplied + '\'' +
                ", adjustedQuestionScores=" + adjustedQuestionScores +
                '}';
    }
}
