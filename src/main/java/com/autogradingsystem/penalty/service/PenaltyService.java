package com.autogradingsystem.penalty.service;

import com.autogradingsystem.penalty.model.PenaltyGradingResult;
import com.autogradingsystem.penalty.model.PenaltyRecord;
import com.autogradingsystem.penalty.model.ProcessedScore;
import com.autogradingsystem.penalty.strategies.PenaltyStrategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates penalty computation with either strategy-based or CSV-backed rules.
 */
public class PenaltyService {
    private static final double ROOT_FOLDER_DEDUCTION_RATE = 0.20;
    private static final double HIERARCHY_DEDUCTION_RATE = 0.05;
    private static final double HEADER_DEDUCTION_RATE = 0.20;
    private static final double WRONG_PACKAGE_DEDUCTION_RATE = 0.20;

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

        List<PenaltyGradingResult> single = new ArrayList<>();
        single.add(result);
        return processPenaltiesWithGlobalDeductions("single", single, null);
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
        double totalRawScore = 0.0;
        double totalDeduction = 0.0;
        Map<String, Double> rawQuestionScores = new LinkedHashMap<>();
        Map<String, Double> adjustedQuestionScores = new LinkedHashMap<>();
        boolean applyRootFolderPenalty = false;
        List<String> hierarchyQuestions = new ArrayList<>();
        List<String> headerQuestions = new ArrayList<>();
        List<String> wrongPackageQuestions = new ArrayList<>();

        for (PenaltyGradingResult questionResult : questionResults) {
            if (questionResult == null) {
                throw new IllegalArgumentException("Question result cannot be null");
            }

            totalRawScore += questionResult.getRawScore();
            if (questionResult.getQuestionId() != null) {
                double roundedRaw = round2(questionResult.getRawScore());
                rawQuestionScores.put(questionResult.getQuestionId(), roundedRaw);
                adjustedQuestionScores.put(questionResult.getQuestionId(), roundedRaw);
            }
            if (!questionResult.isRootFolderCorrect()) {
                applyRootFolderPenalty = true;
            }
        }

        for (PenaltyGradingResult questionResult : questionResults) {
            String qid = questionResult.getQuestionId() == null ? "Question" : questionResult.getQuestionId();
            double rawScore = questionResult.getRawScore();
            double adjustedScore = adjustedQuestionScores.getOrDefault(qid, round2(rawScore));

            if (!questionResult.hasProperHierarchy()) {
                double deduction = round2(rawScore * HIERARCHY_DEDUCTION_RATE);
                totalDeduction += deduction;
                adjustedScore = round2(adjustedScore - deduction);
                hierarchyQuestions.add(qid);
            }

            if (!questionResult.hasHeaders()) {
                double deduction = round2(rawScore * HEADER_DEDUCTION_RATE);
                totalDeduction += deduction;
                adjustedScore = round2(adjustedScore - deduction);
                headerQuestions.add(qid);
            }

            if (questionResult.hasWrongPackage()) {
                double deduction = round2(rawScore * WRONG_PACKAGE_DEDUCTION_RATE);
                totalDeduction += deduction;
                adjustedScore = round2(adjustedScore - deduction);
                wrongPackageQuestions.add(qid);
            }

            adjustedQuestionScores.put(qid, Math.max(0.0, adjustedScore));
        }

        if (applyRootFolderPenalty) {
            totalDeduction += round2(totalRawScore * ROOT_FOLDER_DEDUCTION_RATE);
        }

        applyDisplayOverrides(adjustedQuestionScores, rawQuestionScores, wrongPackageQuestions);

        List<PenaltyRecord> externalPenalties = loadExternalPenalties(studentId, penaltiesCsvPath);
        double externalAdjustment = 0.0;
        for (PenaltyRecord penalty : externalPenalties) {
            externalAdjustment += penalty.getPenaltyValue();
        }

        double finalScore = Math.max(0.0, round2(totalRawScore - totalDeduction + externalAdjustment));
        String rulesSummary = buildPenaltySummary(finalScore, adjustedQuestionScores, rawQuestionScores,
                applyRootFolderPenalty, hierarchyQuestions, headerQuestions, wrongPackageQuestions, externalPenalties);
        return new ProcessedScore(totalRawScore, round2(totalDeduction), finalScore,
                rulesSummary, adjustedQuestionScores);
    }

    public int getStrategyCount() {
        return strategies.size();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String fmt(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String buildPenaltySummary(double finalScore,
                                       Map<String, Double> adjustedQuestionScores,
                                       Map<String, Double> rawQuestionScores,
                                       boolean applyRootFolderPenalty,
                                       List<String> hierarchyQuestions,
                                       List<String> headerQuestions,
                                       List<String> wrongPackageQuestions,
                                       List<PenaltyRecord> externalPenalties) {
        List<String> summaries = new ArrayList<>();
        double totalRawScore = rawQuestionScores.values().stream().mapToDouble(Double::doubleValue).sum();

        if (applyRootFolderPenalty) {
            double adjustedTotal = round2(totalRawScore * (1 - ROOT_FOLDER_DEDUCTION_RATE));
            summaries.add("Penalty 1: total raw " + fmtCompact(totalRawScore)
                    + " - (" + fmtCompact(totalRawScore) + " * 20%) = "
                    + fmtCompact(adjustedTotal));
        }
        if (!hierarchyQuestions.isEmpty()) {
            summaries.add("Penalty 2: " + describeHierarchyPenalty(hierarchyQuestions, rawQuestionScores));
        }
        if (!headerQuestions.isEmpty()) {
            summaries.add("Penalty 3: " + describeHeaderPenalty(headerQuestions, adjustedQuestionScores, rawQuestionScores));
        }
        if (!wrongPackageQuestions.isEmpty()) {
            summaries.add("Penalty 4: " + describeWrongPackagePenalty(wrongPackageQuestions, adjustedQuestionScores, rawQuestionScores));
        }
        summaries.addAll(describeExternalPenalties(externalPenalties));

        return summaries.isEmpty() ? "No penalty" : String.join("; ", summaries);
    }

    private List<String> describeExternalPenalties(List<PenaltyRecord> externalPenalties) {
        if (externalPenalties == null || externalPenalties.isEmpty()) {
            return Collections.emptyList();
        }
        return externalPenalties.stream()
                .map(penalty -> {
                    double value = Math.abs(penalty.getPenaltyValue());
                    String op = penalty.getPenaltyValue() < 0 ? "-" : "+";
                    return penalty.getReason() + ": final score " + op + " " + fmtCompact(value);
                })
                .toList();
    }

    private void applyDisplayOverrides(Map<String, Double> adjustedQuestionScores,
                                       Map<String, Double> rawQuestionScores,
                                       List<String> wrongPackageQuestions) {
        List<String> uniqueWrongPackageQuestions = wrongPackageQuestions.stream().distinct().toList();

        if (uniqueWrongPackageQuestions.size() != 1) {
            for (String qid : uniqueWrongPackageQuestions) {
                adjustedQuestionScores.put(qid, rawQuestionScores.getOrDefault(qid, 0.0));
            }
        }
    }

    private String describeHierarchyPenalty(List<String> questionIds, Map<String, Double> rawQuestionScores) {
        Map<String, Double> groupedTotals = new LinkedHashMap<>();
        for (String qid : questionIds.stream().distinct().toList()) {
            String group = parentQuestionId(qid);
            groupedTotals.merge(group, rawQuestionScores.getOrDefault(qid, 0.0), (a, b) -> a + b);
        }

        return groupedTotals.entrySet().stream()
                .map(entry -> {
                    double raw = round2(entry.getValue());
                    double adjusted = round2(raw * (1 - HIERARCHY_DEDUCTION_RATE));
                    return entry.getKey().toLowerCase(Locale.US) + " raw " + fmtCompact(raw)
                            + " - (" + fmtCompact(raw) + " * 5%) = " + fmtCompact(adjusted);
                })
                .collect(Collectors.joining(", "));
    }

    private String describeHeaderPenalty(List<String> questionIds,
                                         Map<String, Double> adjustedQuestionScores,
                                         Map<String, Double> rawQuestionScores) {
        return questionIds.stream()
                .distinct()
                .map(qid -> {
                    double raw = rawQuestionScores.getOrDefault(qid, 0.0);
                    double adjusted = adjustedQuestionScores.getOrDefault(qid, 0.0);
                    return qid.toLowerCase(Locale.US) + " raw " + fmtCompact(raw)
                            + " - (" + fmtCompact(raw) + " * 20%) = " + fmtCompact(adjusted);
                })
                .collect(Collectors.joining(", "));
    }

    private String describeWrongPackagePenalty(List<String> questionIds,
                                               Map<String, Double> adjustedQuestionScores,
                                               Map<String, Double> rawQuestionScores) {
        return questionIds.stream()
                .distinct()
                .map(qid -> {
                    double raw = rawQuestionScores.getOrDefault(qid, 0.0);
                    double adjusted = adjustedQuestionScores.getOrDefault(qid, 0.0);
                    return qid.toLowerCase(Locale.US) + " raw " + fmtCompact(raw)
                            + " - (" + fmtCompact(raw) + " * 20%) = " + fmtCompact(adjusted);
                })
                .collect(Collectors.joining(", "));
    }

    private String parentQuestionId(String questionId) {
        if (questionId == null || questionId.isBlank()) {
            return "question";
        }
        if (questionId.length() > 2) {
            return questionId.substring(0, 2);
        }
        return questionId;
    }

    private String fmtCompact(double value) {
        double rounded = round2(value);
        if (Math.abs(rounded - Math.rint(rounded)) < 1e-9) {
            return String.format(Locale.US, "%.0f", rounded);
        }
        return fmt(rounded);
    }

    private List<PenaltyRecord> loadExternalPenalties(String studentId, String penaltiesCsvPath) {
        if (studentId == null || penaltiesCsvPath == null || penaltiesCsvPath.isBlank()) {
            return Collections.emptyList();
        }

        Path path = Path.of(penaltiesCsvPath);
        if (!Files.exists(path)) {
            return Collections.emptyList();
        }

        String normalizedStudentId = normalizeStudentId(studentId);
        List<PenaltyRecord> matches = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] parts = trimmed.split(",", 3);
                if (parts.length < 2) {
                    continue;
                }

                String csvStudentId = normalizeStudentId(parts[0]);
                if (!normalizedStudentId.equals(csvStudentId)) {
                    continue;
                }

                try {
                    double penaltyValue = Double.parseDouble(parts[1].trim());
                    String reason = parts.length >= 3 && !parts[2].trim().isEmpty()
                            ? parts[2].trim()
                            : "Manual penalty";
                    matches.add(new PenaltyRecord(csvStudentId, penaltyValue, reason, false));
                } catch (NumberFormatException ignored) {
                    // Skip malformed entries and continue processing the rest.
                }
            }
        } catch (IOException ignored) {
            return Collections.emptyList();
        }

        return matches;
    }

    private String normalizeStudentId(String studentId) {
        return studentId == null ? "" : studentId.trim().replace("#", "").toLowerCase(Locale.US);
    }
}
