package com.autogradingsystem.penalty.service;

import com.autogradingsystem.penalty.model.PenaltyGradingResult;
import com.autogradingsystem.penalty.model.ProcessedScore;
import com.autogradingsystem.penalty.strategies.PenaltyStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates penalty computation with either strategy-based or CSV-backed rules.
 */
public class PenaltyService {
    private final List<PenaltyStrategy> strategies;

    public PenaltyService() {
        this.strategies = new ArrayList<>();
    }

    public PenaltyService registerStrategy(PenaltyStrategy strategy) {
        if (strategy != null) {
            this.strategies.add(strategy);
        }
        return this;
    }

    public ProcessedScore processPenalties(PenaltyGradingResult result) {
        if (result == null) {
            throw new IllegalArgumentException("PenaltyGradingResult cannot be null");
        }

        double totalDeduction = 0.0;
        for (PenaltyStrategy strategy : strategies) {
            totalDeduction += strategy.calculateDeduction(result);
        }

        double finalScore = Math.max(0.0, result.getRawScore() - totalDeduction);
        return new ProcessedScore(result.getRawScore(), totalDeduction, finalScore);
    }

    public ProcessedScore processPenaltiesWithGlobalDeductions(
            String studentId,
            List<PenaltyGradingResult> questionResults,
            String penaltiesCsvPath) {
        if (studentId == null || studentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Student ID cannot be null or empty");
        }
        if (questionResults == null || questionResults.isEmpty()) {
            throw new IllegalArgumentException("Question results cannot be null or empty");
        }
        if (penaltiesCsvPath == null || penaltiesCsvPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Penalties CSV path cannot be null or empty");
        }

        PenaltyCalculator calculator = new PenaltyCalculator();
        calculator.loadExternalPenalties(penaltiesCsvPath);

        double totalRawScore = 0.0;
        double totalAfterQuestionPenalties = 0.0;

        for (int i = 0; i < questionResults.size(); i++) {
            PenaltyGradingResult questionResult = questionResults.get(i);
            if (questionResult == null) {
                throw new IllegalArgumentException("Question result at index " + i + " cannot be null");
            }

            totalRawScore += questionResult.getRawScore();
            String questionName = "Q" + (i + 1);

            totalAfterQuestionPenalties += calculator.calculateQuestionScore(
                    questionName,
                    questionResult.getRawScore(),
                    !questionResult.hasProperHierarchy(),
                    !questionResult.hasHeaders(),
                    questionResult.hasCompilationError());
        }

        double finalScore = calculator.calculateFinalTotal(studentId, totalAfterQuestionPenalties);
        return new ProcessedScore(totalRawScore, totalRawScore - finalScore, finalScore);
    }

    public int getStrategyCount() {
        return strategies.size();
    }
}
