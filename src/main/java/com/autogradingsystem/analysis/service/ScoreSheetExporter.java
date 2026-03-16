package com.autogradingsystem.analysis.service;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.model.GradingResult;
import com.autogradingsystem.model.Student;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * ScoreSheetExporter
 *
 * Produces the OFFICIAL score sheet XLSX with two tabs:
 *   Sheet 1 "Score Sheet" - All students from master CSV with scores + remarks
 *   Sheet 2 "Anomalies"   - Unidentified submissions (no rename + no headers)
 */
public class ScoreSheetExporter {

    private static final int COL_USERNAME  = 1;
    private static final int COL_NUMERATOR = 5;
    private static final int COL_EOL       = 7;

    public Path export(Map<String, List<GradingResult>> resultsByStudent,
                       Map<String, String> remarksByStudent,
                       Map<String, String> anomalyRemarks,
                       List<Student> allStudents) throws IOException {

        Path outputDir  = PathConfig.OUTPUT_BASE.resolve("reports").toAbsolutePath();
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("IS442-ScoreSheet-Updated.xlsx");

        List<String> questionOrder = buildQuestionOrder(resultsByStudent);

        Map<String, Double>              totals    = new LinkedHashMap<>();
        Map<String, Map<String, Double>> qScoreMap = new LinkedHashMap<>();
        buildScoreMaps(resultsByStudent, totals, qScoreMap);

        // Compute maxScores from tester files
        Map<String, Double> maxScores = new LinkedHashMap<>();
        for (Map.Entry<String, List<GradingResult>> entry : resultsByStudent.entrySet())
            for (GradingResult r : entry.getValue())
                maxScores.computeIfAbsent(r.getTask().getQuestionId(),
                    k -> ScoreAnalyzer.getMaxScoreFromTester(k));

        // Set of all usernames actually graded (identified students only).
        // Any CSV username not in this set has no matching submission.
        Set<String> gradedUsernames = totals.keySet();

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet mainSheet = workbook.createSheet("Score Sheet");
            buildMainSheet(mainSheet, workbook, questionOrder, totals, qScoreMap,
                           remarksByStudent, gradedUsernames);

            Sheet anomalySheet = workbook.createSheet("Anomalies");
            buildAnomalySheet(anomalySheet, workbook, allStudents, questionOrder,
                              qScoreMap, anomalyRemarks, maxScores);

            try (OutputStream os = Files.newOutputStream(outputFile)) {
                workbook.write(os);
            }
        }

        return outputFile;
    }

    // ── Sheet 1: Main score sheet ──────────────────────────────────────────

    private void buildMainSheet(Sheet sheet, Workbook workbook,
                                List<String> questionOrder,
                                Map<String, Double> totals,
                                Map<String, Map<String, Double>> qScoreMap,
                                Map<String, String> remarksByStudent,
                                Set<String> gradedUsernames) throws IOException {

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(PathConfig.CSV_SCORESHEET.toAbsolutePath()),
                StandardCharsets.UTF_8))) {

            String line;
            boolean isHeader = true;
            int rowIdx = 0;

            while ((line = reader.readLine()) != null) {
                if (rowIdx == 0 && line.startsWith("\uFEFF")) line = line.substring(1);

                String[] cols = line.split(",", -1);
                Row row = sheet.createRow(rowIdx++);

                if (isHeader) {
                    int cellIdx = 0;
                    for (int c = 0; c < cols.length; c++) {
                        if (c == COL_EOL) continue;
                        Cell cell = row.createCell(cellIdx++);
                        cell.setCellValue(cols[c].trim());
                        cell.setCellStyle(headerStyle);
                    }
                    for (String qid : questionOrder) {
                        Cell cell = row.createCell(cellIdx++);
                        cell.setCellValue(qid);
                        cell.setCellStyle(headerStyle);
                    }
                    Cell rh = row.createCell(cellIdx++);
                    rh.setCellValue("Remarks");
                    rh.setCellStyle(headerStyle);
                    Cell eh = row.createCell(cellIdx);
                    eh.setCellValue(cols.length > COL_EOL ? cols[COL_EOL].trim() : "#");
                    eh.setCellStyle(headerStyle);
                    isHeader = false;
                    continue;
                }

                String eolValue = cols.length > COL_EOL ? cols[COL_EOL].trim() : "#";
                String raw      = cols.length > COL_USERNAME ? cols[COL_USERNAME].trim() : "";
                String username = (raw.startsWith("#") ? raw.substring(1) : raw).trim();

                if (totals.containsKey(username)) {
                    double t = totals.get(username);
                    cols[COL_NUMERATOR] = fmtNum(t);
                }

                int cellIdx = 0;
                for (int c = 0; c < cols.length; c++) {
                    if (c == COL_EOL) continue;
                    row.createCell(cellIdx++).setCellValue(cols[c].trim());
                }

                Map<String, Double> qScores = qScoreMap.getOrDefault(username, Collections.emptyMap());
                for (String qid : questionOrder) {
                    row.createCell(cellIdx++).setCellValue(qScores.getOrDefault(qid, 0.0));
                }

                // Remarks logic:
                // - Student graded normally → show their remarks (or empty if all passed)
                // - Student not in graded set → no matching submission found (truly absent
                //   or submission was unidentifiable and is in the Anomalies tab)
                String remarks;
                if (!gradedUsernames.contains(username)) {
                    remarks = "Missing submission - refer to Anomalies tab";
                } else {
                    remarks = remarksByStudent.getOrDefault(username, "");
                }
                row.createCell(cellIdx++).setCellValue(remarks);
                row.createCell(cellIdx).setCellValue(eolValue);
            }
        }

        int totalCols = 7 + questionOrder.size() + 2;
        for (int c = 0; c < totalCols; c++) sheet.autoSizeColumn(c);
    }

    // ── Sheet 2: Anomalies ─────────────────────────────────────────────────

    private void buildAnomalySheet(Sheet anomalySheet, Workbook workbook,
                                   List<Student> allStudents,
                                   List<String> questionOrder,
                                   Map<String, Map<String, Double>> qScoreMap,
                                   Map<String, String> anomalyRemarks,
                                   Map<String, Double> maxScores) {

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        Row header = anomalySheet.createRow(0);
        int c = 0;
        Cell h0 = header.createCell(c++); h0.setCellValue("FolderName");                        h0.setCellStyle(headerStyle);
        Cell h1 = header.createCell(c++); h1.setCellValue("Calculated Final Grade Numerator");   h1.setCellStyle(headerStyle);
        Cell h2 = header.createCell(c++); h2.setCellValue("Calculated Final Grade Denominator"); h2.setCellStyle(headerStyle);
        for (String qid : questionOrder) {
            Cell hq = header.createCell(c++); hq.setCellValue(qid); hq.setCellStyle(headerStyle);
        }
        Cell hRemarks = header.createCell(c++); hRemarks.setCellValue("Remarks");       hRemarks.setCellStyle(headerStyle);
        Cell hReason  = header.createCell(c);   hReason.setCellValue("Anomaly Reason"); hReason.setCellStyle(headerStyle);

        double denominator = maxScores.values().stream().mapToDouble(Double::doubleValue).sum();

        int rowIdx = 1;
        for (Student student : allStudents) {
            if (!student.isAnomaly()) continue;

            Row row = anomalySheet.createRow(rowIdx++);
            c = 0;

            row.createCell(c++).setCellValue(
                student.getRawFolderName() != null ? student.getRawFolderName() : student.getId()
            );

            Map<String, Double> qScores = qScoreMap.getOrDefault(student.getId(), Collections.emptyMap());
            double total = qScores.values().stream().mapToDouble(Double::doubleValue).sum();
            row.createCell(c++).setCellValue(total);
            row.createCell(c++).setCellValue(denominator);

            for (String qid : questionOrder) {
                row.createCell(c++).setCellValue(qScores.getOrDefault(qid, 0.0));
            }

            row.createCell(c++).setCellValue(anomalyRemarks.getOrDefault(student.getId(), ""));

            StringBuilder reason = new StringBuilder("Folder not renamed");
            if (student.getMissingHeaderFiles().isEmpty()) {
                reason.append("; No headers found in any submission file");
            } else {
                reason.append("; Missing headers in: ")
                      .append(String.join(", ", student.getMissingHeaderFiles()));
            }
            row.createCell(c).setCellValue(reason.toString());
        }

        int totalCols = 3 + questionOrder.size() + 2;
        for (int col = 0; col < totalCols; col++) anomalySheet.autoSizeColumn(col);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private List<String> buildQuestionOrder(Map<String, List<GradingResult>> resultsByStudent) {
        Set<String> qidSet = new LinkedHashSet<>();
        for (List<GradingResult> results : resultsByStudent.values())
            for (GradingResult r : results)
                qidSet.add(r.getTask().getQuestionId());
        List<String> questionOrder = new ArrayList<>(qidSet);
        questionOrder.sort(ScoreSheetExporter::naturalCompare);
        return questionOrder;
    }

    private void buildScoreMaps(Map<String, List<GradingResult>> resultsByStudent,
                                Map<String, Double> totals,
                                Map<String, Map<String, Double>> qScoreMap) {
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