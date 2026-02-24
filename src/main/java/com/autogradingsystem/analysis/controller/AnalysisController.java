package com.autogradingsystem.analysis.controller;

import com.autogradingsystem.analysis.service.ScoreAnalyzer;
import com.autogradingsystem.analysis.service.ScoreSheetExporter;
import com.autogradingsystem.analysis.service.StatisticsReportExporter;
import com.autogradingsystem.model.GradingResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * AnalysisController
 *
 * Orchestrates the full analysis pipeline:
 *   1. Console display of grading results
 *   2. Export official score sheet  → IS442-ScoreSheet-Updated-{ts}.csv
 *   3. Export statistics report     → IS442-Statistics.xlsx  (bonus feature)
 */
public class AnalysisController {

    private final ScoreSheetExporter       scoreSheetExporter;
    private final StatisticsReportExporter statisticsReportExporter;

    public AnalysisController() {
        this.scoreSheetExporter       = new ScoreSheetExporter();
        this.statisticsReportExporter = new StatisticsReportExporter();
    }

    public void analyzeAndDisplay(List<GradingResult> results) {

        Map<String, Double>              maxScores      = ScoreAnalyzer.inferMaxScores(results);
        List<GradingResult>              updatedResults = ScoreAnalyzer.updateWithMaxScores(results);
        Map<String, List<GradingResult>> byStudent      = ScoreAnalyzer.groupByStudent(updatedResults);

        System.out.println("\n📋 Detected Max Scores per Question:");
        maxScores.forEach((q, m) -> System.out.println("   " + q + ": " + m + " points"));

        displayQuestionStatistics(updatedResults, maxScores);
        displayCompactView(byStudent, maxScores);
        displayOverallStatistics(byStudent, maxScores);

        exportBothReports(byStudent);
    }

    // ── Export ─────────────────────────────────────────────────────────────

    private void exportBothReports(Map<String, List<GradingResult>> byStudent) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📄 EXPORTING REPORTS");
        System.out.println("=".repeat(70));

        // Official score sheet
        try {
            Path scoreSheet = scoreSheetExporter.export(byStudent);
            System.out.println("✅ Score Sheet   → " + scoreSheet.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("❌ Score Sheet export failed: " + e.getMessage());
        }

        // Statistics report
        try {
            Path statsReport = statisticsReportExporter.export(byStudent);
            System.out.println("✅ Statistics    → " + statsReport.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("❌ Statistics export failed: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("=".repeat(70));
    }

    // ── Console display (unchanged from original) ──────────────────────────

    private void displayQuestionStatistics(List<GradingResult> results,
                                           Map<String, Double> maxScores) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📈 QUESTION-BY-QUESTION STATISTICS");
        System.out.println("=".repeat(70));

        Map<String, List<GradingResult>> byQuestion = ScoreAnalyzer.groupByQuestion(results);
        for (Map.Entry<String, List<GradingResult>> entry : byQuestion.entrySet()) {
            String qid             = entry.getKey();
            List<GradingResult> qr = entry.getValue();
            double max     = maxScores.getOrDefault(qid, 0.0);
            double avg     = ScoreAnalyzer.calculateAverageScore(qr);
            long perfect   = ScoreAnalyzer.countPerfect(qr);
            long passed    = ScoreAnalyzer.countPassed(qr);
            long failed    = ScoreAnalyzer.countFailed(qr);
            int total      = qr.size();
            String pct     = max > 0 ? String.format("%.1f%%", avg / max * 100) : "N/A";

            System.out.println("\n" + qid + " (" + max + " points):");
            System.out.println("  Average: " + avg + " / " + max + " (" + pct + ")");
            System.out.println("  Perfect scores: " + perfect + "/" + total + " students");
            if (passed - perfect > 0)
                System.out.println("  Partial credit: " + (passed - perfect) + "/" + total + " students");
            System.out.println("  Failed: " + failed + "/" + total + " students");
        }
    }

    private void displayCompactView(Map<String, List<GradingResult>> byStudent,
                                    Map<String, Double> maxScores) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📋 GRADING RESULTS - COMPACT VIEW");
        System.out.println("=".repeat(70));

        double totalMax = maxScores.values().stream().mapToDouble(Double::doubleValue).sum();
        for (Map.Entry<String, List<GradingResult>> entry : byStudent.entrySet()) {
            double total = ScoreAnalyzer.calculateTotalScore(entry.getValue());
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-20s", entry.getKey() + ":"));
            for (GradingResult r : entry.getValue())
                sb.append(String.format("  %s:%-5s", r.getTask().getQuestionId(), r.getScore()));
            sb.append(String.format("  Total: %.1f/%.1f", total, totalMax));
            System.out.println(sb);
        }
        System.out.println("=".repeat(70));
    }

    private void displayOverallStatistics(Map<String, List<GradingResult>> byStudent,
                                          Map<String, Double> maxScores) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📊 OVERALL CLASS STATISTICS");
        System.out.println("=".repeat(70));

        double totalMax    = maxScores.values().stream().mapToDouble(Double::doubleValue).sum();
        double classTotal  = 0;
        double highest     = Double.MIN_VALUE, lowest = Double.MAX_VALUE;
        String highStudent = "", lowStudent = "";
        int passCount = 0, studentCount = byStudent.size();

        for (Map.Entry<String, List<GradingResult>> entry : byStudent.entrySet()) {
            double score = ScoreAnalyzer.calculateTotalScore(entry.getValue());
            classTotal += score;
            if (score > highest) { highest = score; highStudent = entry.getKey(); }
            if (score < lowest)  { lowest  = score; lowStudent  = entry.getKey(); }
            if (totalMax > 0 && score / totalMax >= 0.5) passCount++;
        }

        double avg = studentCount > 0 ? classTotal / studentCount : 0;
        System.out.println("\nClass Average : " + String.format("%.1f", avg) + " / " + totalMax
                + " (" + String.format("%.1f%%", totalMax > 0 ? avg / totalMax * 100 : 0) + ")");
        System.out.println("Highest Score : " + highest + " / " + totalMax + " (" + highStudent + ")");
        System.out.println("Lowest Score  : " + lowest  + " / " + totalMax + " (" + lowStudent  + ")");
        System.out.println("Pass Rate     : " + String.format("%d/%d students (%.1f%%)",
                passCount, studentCount,
                studentCount > 0 ? (double) passCount / studentCount * 100 : 0));
        System.out.println("=".repeat(70));
    }
}