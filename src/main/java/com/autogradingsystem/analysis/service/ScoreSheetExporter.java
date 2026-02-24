package com.autogradingsystem.analysis.service;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.model.GradingResult;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * ScoreSheetExporter
 *
 * Produces the OFFICIAL score sheet CSV:
 *   - Copies original IS442-ScoreSheet.csv structure
 *   - Fills numerator (total score) for each student
 *   - Appends individual per-question score columns
 *
 * Output: resources/output/reports/IS442-ScoreSheet-Updated-{timestamp}.csv
 */
public class ScoreSheetExporter {

    private static final int COL_USERNAME  = 1;
    private static final int COL_NUMERATOR = 5;

    public Path export(Map<String, List<GradingResult>> resultsByStudent) throws IOException {

        Path outputDir = PathConfig.OUTPUT_BASE.resolve("reports").toAbsolutePath();
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("IS442-ScoreSheet-Updated.csv");

        // Collect question IDs in natural order
        List<String> questionOrder = new ArrayList<>();
        Set<String>  qidSet        = new LinkedHashSet<>();
        for (List<GradingResult> results : resultsByStudent.values())
            for (GradingResult r : results)
                qidSet.add(r.getTask().getQuestionId());
        questionOrder.addAll(qidSet);
        questionOrder.sort(ScoreSheetExporter::naturalCompare);

        // Compute totals and per-question scores per student
        Map<String, Double>              totals      = new LinkedHashMap<>();
        Map<String, Map<String, Double>> qScoreMap   = new LinkedHashMap<>();

        for (Map.Entry<String, List<GradingResult>> entry : resultsByStudent.entrySet()) {
            double total = 0.0;
            Map<String, Double> qScores = new LinkedHashMap<>();
            for (GradingResult r : entry.getValue()) {
                total += r.getScore();
                qScores.put(r.getTask().getQuestionId(), r.getScore());
            }
            totals.put(entry.getKey(), total);
            qScoreMap.put(entry.getKey(), qScores);
        }

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(outputFile), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(PathConfig.CSV_SCORESHEET.toAbsolutePath()),
                StandardCharsets.UTF_8))) {

            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(",", -1);

                if (isHeader) {
                    writer.write(line);
                    for (String qid : questionOrder) writer.write("," + qid);
                    writer.newLine();
                    isHeader = false;
                    continue;
                }

                // Fill numerator column
                if (cols.length > COL_USERNAME) {
                    String raw      = cols[COL_USERNAME].trim();
                    String username = raw.startsWith("#") ? raw.substring(1) : raw;
                    if (totals.containsKey(username)) {
                        double t = totals.get(username);
                        cols[COL_NUMERATOR] = t == Math.floor(t)
                                ? String.valueOf((int) t) : String.format("%.2f", t);
                    }
                }

                writer.write(String.join(",", cols));

                // Append per-question scores
                String raw      = cols.length > COL_USERNAME ? cols[COL_USERNAME].trim() : "";
                String username = raw.startsWith("#") ? raw.substring(1) : raw;
                Map<String, Double> qScores = qScoreMap.getOrDefault(username, Collections.emptyMap());
                for (String qid : questionOrder) {
                    double s = qScores.getOrDefault(qid, 0.0);
                    writer.write("," + fmtNum(s));
                }
                writer.newLine();
            }
        }

        return outputFile;
    }

    static String fmtNum(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.2f", v);
    }

    static int naturalCompare(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int na = 0, nb = 0;
                while (i < a.length() && Character.isDigit(a.charAt(i))) na = na * 10 + (a.charAt(i++) - '0');
                while (j < b.length() && Character.isDigit(b.charAt(j))) nb = nb * 10 + (b.charAt(j++) - '0');
                if (na != nb) return na - nb;
            } else {
                if (ca != cb) return ca - cb;
                i++; j++;
            }
        }
        return a.length() - b.length();
    }
}