package com.autogradingsystem.analysis.controller;

import com.autogradingsystem.analysis.service.ScoreAnalyzer;
import com.autogradingsystem.analysis.service.ScoreSheetExporter;
import com.autogradingsystem.model.GradingResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * AnalysisController - Brain for Analysis Service
 *
 * PURPOSE:
 * - Analyzes grading results and displays statistics
 * - Exports updated score sheet CSV at the end of each run
 * - Coordinates results analysis and display
 * - Acts as entry point for analysis service
 * - Called by Main.java after grading complete
 *
 * WORKFLOW:
 * 1. Infer max scores per question
 * 2. Display per-question statistics
 * 3. Display compact results view
 * 4. Display overall class statistics
 * 5. Export updated IS442-ScoreSheet CSV → resources/output/
 */

public class AnalysisController {

    private final ScoreSheetExporter scoreSheetExporter;

    public AnalysisController() {
        this.scoreSheetExporter = new ScoreSheetExporter();
    }

    /**
     * Main entry point — analyzes results, prints all stats, then exports score sheet.
     *
     * @param results All GradingResult objects from ExecutionController
     */
    public void analyzeAndDisplay(List<GradingResult> results) {

        // ── Infer max scores and update results ──────────────────────────────
        Map<String, Double> maxScores = ScoreAnalyzer.inferMaxScores(results);
        List<GradingResult> updatedResults = ScoreAnalyzer.updateWithMaxScores(results);

        System.out.println("\n📋 Detected Max Scores per Question:");
        for (Map.Entry<String, Double> entry : maxScores.entrySet()) {
            System.out.println("   " + entry.getKey() + ": " + entry.getValue() + " points");
        }

        // ── Per-question statistics ───────────────────────────────────────────
        displayQuestionStatistics(updatedResults, maxScores);

        // ── Compact results view ──────────────────────────────────────────────
        Map<String, List<GradingResult>> byStudent =
                ScoreAnalyzer.groupByStudent(updatedResults);
        displayCompactView(byStudent, maxScores);

        // ── Overall class statistics ──────────────────────────────────────────
        displayOverallStatistics(byStudent, maxScores);

        // ── Export updated score sheet ────────────────────────────────────────
        exportScoreSheet(byStudent);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private: display methods (unchanged from original)
    // ─────────────────────────────────────────────────────────────────────────

    private void displayQuestionStatistics(List<GradingResult> results,
                                           Map<String, Double> maxScores) {

        System.out.println("\n" + "=".repeat(70));
        System.out.println("📈 QUESTION-BY-QUESTION STATISTICS");
        System.out.println("=".repeat(70));

        Map<String, List<GradingResult>> byQuestion = ScoreAnalyzer.groupByQuestion(results);

        for (Map.Entry<String, List<GradingResult>> entry : byQuestion.entrySet()) {
            String qid = entry.getKey();
            List<GradingResult> qResults = entry.getValue();
            double max = maxScores.getOrDefault(qid, 0.0);

            double avg = ScoreAnalyzer.calculateAverageScore(qResults);
            long perfect = ScoreAnalyzer.countPerfect(qResults);
            long passed = ScoreAnalyzer.countPassed(qResults);
            long failed = ScoreAnalyzer.countFailed(qResults);
            int total = qResults.size();

            String pct = max > 0 ? String.format("%.1f%%", avg / max * 100) : "N/A";

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
            String student = entry.getKey();
            List<GradingResult> sResults = entry.getValue();
            double total = ScoreAnalyzer.calculateTotalScore(sResults);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-20s", student + ":"));
            for (GradingResult r : sResults) {
                sb.append(String.format("  %s:%-5s", r.getTask().getQuestionId(), r.getScore()));
            }
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

        double totalMax = maxScores.values().stream().mapToDouble(Double::doubleValue).sum();

        double classTotal = 0;
        double highest = Double.MIN_VALUE;
        double lowest = Double.MAX_VALUE;
        String highStudent = "", lowStudent = "";
        int passCount = 0;
        int studentCount = byStudent.size();

        for (Map.Entry<String, List<GradingResult>> entry : byStudent.entrySet()) {
            double score = ScoreAnalyzer.calculateTotalScore(entry.getValue());
            classTotal += score;
            if (score > highest) { highest = score; highStudent = entry.getKey(); }
            if (score < lowest)  { lowest = score;  lowStudent  = entry.getKey(); }
            if (totalMax > 0 && score / totalMax >= 0.5) passCount++;
        }

        double avg = studentCount > 0 ? classTotal / studentCount : 0;
        String passRate = totalMax > 0
                ? String.format("%d/%d students (%.1f%%)", passCount, studentCount,
                                (double) passCount / studentCount * 100)
                : "N/A";

        System.out.println("\nClass Average: " + String.format("%.1f", avg) + " / " + totalMax
                + " (" + String.format("%.1f%%", totalMax > 0 ? avg / totalMax * 100 : 0) + ")");
        System.out.println("Highest Score: " + highest + " / " + totalMax + " (" + highStudent + ")");
        System.out.println("Lowest Score:  " + lowest  + " / " + totalMax + " (" + lowStudent  + ")");
        System.out.println("Pass Rate (≥50%): " + passRate);
        System.out.println("=".repeat(70));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private: score sheet export
    // ─────────────────────────────────────────────────────────────────────────

    private void exportScoreSheet(Map<String, List<GradingResult>> byStudent) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📄 EXPORTING SCORE SHEET");
        System.out.println("=".repeat(70));

        try {
            Path output = scoreSheetExporter.export(byStudent);
            System.out.println("✅ Score sheet saved to: " + output.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("❌ Failed to export score sheet: " + e.getMessage());
        }

        System.out.println("=".repeat(70));
    }
}