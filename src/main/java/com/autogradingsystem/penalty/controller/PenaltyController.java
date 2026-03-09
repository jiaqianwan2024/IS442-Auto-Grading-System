package com.autogradingsystem.penalty.controller;

import com.autogradingsystem.penalty.model.PenaltyGradingResult;
import com.autogradingsystem.penalty.model.ProcessedScore;
import com.autogradingsystem.penalty.service.PenaltyService;
import com.autogradingsystem.penalty.strategies.CompilationPenalty;
import com.autogradingsystem.penalty.strategies.StructuralPenalty;

import java.util.List;

/**
 * Entry-point controller for the penalty module.
 *
 * Responsibilities:
 * - Configure default strategies for strategy-based penalties
 * - Expose high-level methods for single-question and per-student processing
 * - Keep orchestration logic out of calculator/strategy classes
 */
public class PenaltyController {
    public static final String DEFAULT_PENALTIES_CSV = "resources/input/penalties.csv";

    private final PenaltyService penaltyService;

    public PenaltyController() {
        this.penaltyService = new PenaltyService()
                .registerStrategy(new StructuralPenalty())
                .registerStrategy(new CompilationPenalty());
    }

    /**
     * Applies configured strategy-based deductions to one result.
     */
    public ProcessedScore processSingleResult(PenaltyGradingResult result) {
        return penaltyService.processPenalties(result);
    }

    /**
     * Applies per-question deductions and then external/global deductions from default CSV.
     */
    public ProcessedScore processStudentResults(String studentId, List<PenaltyGradingResult> questionResults) {
        return processStudentResults(studentId, questionResults, DEFAULT_PENALTIES_CSV);
    }

    /**
     * Applies per-question deductions and then external/global deductions from given CSV.
     */
    public ProcessedScore processStudentResults(
            String studentId,
            List<PenaltyGradingResult> questionResults,
            String penaltiesCsvPath) {
        return penaltyService.processPenaltiesWithGlobalDeductions(
                studentId,
                questionResults,
                penaltiesCsvPath
        );
    }

    public int getConfiguredStrategyCount() {
        return penaltyService.getStrategyCount();
    }
}
