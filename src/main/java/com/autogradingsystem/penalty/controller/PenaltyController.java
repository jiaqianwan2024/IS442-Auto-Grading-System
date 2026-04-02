package com.autogradingsystem.penalty.controller;

import com.autogradingsystem.penalty.model.PenaltyGradingResult;
import com.autogradingsystem.penalty.model.ProcessedScore;
import com.autogradingsystem.penalty.service.PenaltyRemarksParser;
import com.autogradingsystem.penalty.service.PenaltyService;
import com.autogradingsystem.penalty.strategies.CompilationPenalty;
import com.autogradingsystem.penalty.strategies.StructuralPenalty;

import java.util.List;
import java.util.Map;

/**
 * Entry-point controller for the penalty module.
 *
 * Responsibilities:
 * - Configure default strategies for strategy-based penalties
 * - Expose high-level methods for single-question and per-student processing
 * - Keep orchestration logic out of calculator/strategy classes
 */
public class PenaltyController {
    public static final String DEFAULT_PENALTIES_CSV = "config/penalties.csv";

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

    /**
     * Applies all 4 remark-based penalty rules to one student's submission.
     *
     * @param studentId        student identifier (for console logging)
     * @param qScores          question ID → raw earned score
     * @param qMaxScores       question ID → max possible score
     * @param totalDenominator sum of all question max scores
     * @param rawTotal         sum of all raw earned scores
     * @param gradeRemark      remark string from ExecutionController
     * @return {@link PenaltyRemarksParser.PenaltyResult} with adjusted totals, per-Q scores, and remarks
     */
    public PenaltyRemarksParser.PenaltyResult processWithRemarks(
            String studentId,
            Map<String, Double> qScores,
            Map<String, Double> qMaxScores,
            double totalDenominator,
            double rawTotal,
            String gradeRemark) {
        PenaltyRemarksParser.PenaltyResult result =
                new PenaltyRemarksParser().parse(gradeRemark, qScores, qMaxScores,
                                                 totalDenominator, rawTotal);
        if (result.adjustedTotal < rawTotal) {
            System.out.printf("[Penalty] %s: raw=%.2f → final=%.2f | %s%n",
                    studentId, rawTotal, result.adjustedTotal, result.remarks);
        }
        return result;
    }
}
