package com.autogradingsystem.analysis.service;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.model.GradingResult;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * ScoreSheetExporter - Writes Final Scores into the Official Score Sheet CSV
 *
 * PURPOSE:
 * - Reads the original IS442-ScoreSheet.csv from config/
 * - Fills in "Calculated Final Grade Numerator" for each matched student
 * - Writes updated CSV to resources/output/
 * - Preserves ALL original columns, formatting, and # prefixes exactly
 *
 * OUTPUT FILE:
 * resources/output/IS442-ScoreSheet-Updated-{timestamp}.csv
 *
 * MATCHING STRATEGY:
 * - Strips "#" prefix from Username column (e.g., "#ping.lee.2023" → "ping.lee.2023")
 * - Looks up total score from grading results by username
 * - Unmatched students (not graded) → numerator left blank
 *
 * EDGE CASES HANDLED:
 * - Student in CSV but not in grading results → blank numerator preserved
 * - Student graded but not in CSV → ignored (not our row to fill)
 * - Missing output directory → auto-created
 * - Header row detection → skipped for score lookup
 * - Windows \r\n line endings → handled via BufferedReader
 */
public class ScoreSheetExporter {

    /** Column index of Username in the CSV (0-based) */
    private static final int COL_USERNAME = 1;

    /** Column index of Calculated Final Grade Numerator (0-based) */
    private static final int COL_NUMERATOR = 5;

    /**
     * Exports updated score sheet to resources/output/.
     *
     * @param resultsByStudent Map of username → list of GradingResult
     * @return Path to the written output file
     * @throws IOException if reading/writing fails
     */
    public Path export(Map<String, List<GradingResult>> resultsByStudent) throws IOException {

        // Ensure reports directory exists
        Path outputDir = PathConfig.OUTPUT_EXTRACTED.getParent().resolve("reports");
        Files.createDirectories(outputDir);

        // Build timestamped output filename
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path outputFile = outputDir.resolve("IS442-ScoreSheet-Updated-" + timestamp + ".csv");

        // Calculate total score per student
        Map<String, Double> totals = computeTotals(resultsByStudent);

        // Read original CSV, fill numerator, write to output
        try (
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    Files.newInputStream(PathConfig.CSV_SCORESHEET), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                    Files.newOutputStream(outputFile), StandardCharsets.UTF_8))
        ) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {

                if (firstLine) {
                    // Write header unchanged
                    writer.write(line);
                    writer.newLine();
                    firstLine = false;
                    continue;
                }

                // Split preserving all columns
                String[] cols = line.split(",", -1);  // -1 keeps trailing empty fields

                if (cols.length > COL_USERNAME) {
                    // Strip # prefix from username to match grading results
                    String rawUsername = cols[COL_USERNAME].trim();
                    String username = rawUsername.startsWith("#")
                            ? rawUsername.substring(1)
                            : rawUsername;

                    if (totals.containsKey(username)) {
                        double score = totals.get(username);
                        // Format as integer if whole number (e.g. 20.0 → "20"), else decimal
                        cols[COL_NUMERATOR] = score == Math.floor(score)
                                ? String.valueOf((int) score)
                                : String.valueOf(score);
                    }
                }

                writer.write(String.join(",", cols));
                writer.newLine();
            }
        }

        return outputFile;
    }

    /**
     * Sums all question scores per student from grading results.
     *
     * @param resultsByStudent Grouped results map
     * @return Map of username → total score
     */
    private Map<String, Double> computeTotals(Map<String, List<GradingResult>> resultsByStudent) {
        Map<String, Double> totals = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, List<GradingResult>> entry : resultsByStudent.entrySet()) {
            double total = entry.getValue().stream()
                    .mapToDouble(GradingResult::getScore)
                    .sum();
            totals.put(entry.getKey(), total);
        }
        return totals;
    }
}