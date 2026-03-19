package com.autogradingsystem.analysis.service;

import com.autogradingsystem.model.GradingResult;
import com.autogradingsystem.model.Student;
import com.autogradingsystem.PathConfig;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports a single combined XLSX report containing:
 *   Sheet 1  — Score Sheet      (identified students)
 *   Sheet 2  — Anomalies        (unidentifiable submissions)
 *   Sheet 3  — Dashboard        (class-level statistics)
 *   Sheet 4  — Grade Distribution
 *   Sheet 5  — Question Analysis
 *   Sheet 6  — Student Ranking
 *   Sheet 7  — Performance Matrix
 *
 * NOTE: The former "Anomaly Report" sheet (flagging zero/missing/perfect scores) has been
 * removed per v3.4 requirements. The separate IS442-Statistics.xlsx is no longer produced;
 * all content is combined into IS442-ScoreSheet-Updated.xlsx.
 */
public class ScoreSheetExporter {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Backward-compatible overload — no plagiarism notes. */
    public Path export(
            Map<String, List<GradingResult>> resultsByStudent,
            Map<String, String> remarksByStudent,
            Map<String, String> anomalyRemarks,
            List<Student> allStudents) throws IOException {
        return export(resultsByStudent, remarksByStudent, anomalyRemarks, allStudents, Collections.emptyMap());
    }

    /** Full overload — includes plagiarism notes (v3.3+). */
    public Path export(
            Map<String, List<GradingResult>> resultsByStudent,
            Map<String, String> remarksByStudent,
            Map<String, String> anomalyRemarks,
            List<Student> allStudents,
            Map<String, String> plagiarismNotes) throws IOException {

        Path outputPath = PathConfig.OUTPUT_BASE.resolve("reports").resolve("IS442-ScoreSheet-Updated.xlsx");
        Files.createDirectories(outputPath.getParent());

        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            // --- derive shared data ---
            List<String> questionOrder   = buildQuestionOrder(resultsByStudent);
            Map<String, Double> totals   = buildTotalScoreMap(resultsByStudent);
            Map<String, Map<String, Double>> perQ = buildPerQuestionMap(resultsByStudent);
            Map<String, Double> maxScores = inferMaxScores(resultsByStudent);
            double totalMaxScore = maxScores.values().stream().mapToDouble(Double::doubleValue).sum();

            // --- score sheet sheets ---
            buildMainSheet(wb, resultsByStudent, remarksByStudent, allStudents,
                           plagiarismNotes, questionOrder, totals, perQ, totalMaxScore);
            buildAnomalySheet(wb, resultsByStudent, anomalyRemarks, allStudents,
                              questionOrder, perQ, totalMaxScore);

            // --- statistics sheets (formerly IS442-Statistics.xlsx) ---
            buildDashboardSheet(wb, resultsByStudent, questionOrder, maxScores, totalMaxScore);
            buildGradeDistributionSheet(wb, resultsByStudent, totalMaxScore);
            buildQuestionAnalysisSheet(wb, resultsByStudent, questionOrder, maxScores);
            buildStudentRankingSheet(wb, resultsByStudent, questionOrder, totals, totalMaxScore);
            buildPerformanceMatrixSheet(wb, resultsByStudent, questionOrder, perQ, maxScores);
            // NOTE: Anomaly Report sheet intentionally omitted per v3.4 requirements.

            try (var out = Files.newOutputStream(outputPath)) {
                wb.write(out);
            }
        }

        System.out.println("✅ Combined report exported → " + outputPath);
        return outputPath;
    }

    // =========================================================================
    // SHEET 1 — Score Sheet
    // =========================================================================

    private void buildMainSheet(
            XSSFWorkbook wb,
            Map<String, List<GradingResult>> resultsByStudent,
            Map<String, String> remarksByStudent,
            List<Student> allStudents,
            Map<String, String> plagiarismNotes,
            List<String> questionOrder,
            Map<String, Double> totals,
            Map<String, Map<String, Double>> perQ,
            double totalMaxScore) throws IOException {

        XSSFSheet sheet = wb.createSheet("Score Sheet");
        Set<String> gradedUsernames = resultsByStudent.keySet();

        // styles
        XSSFCellStyle headerStyle = makeHeaderStyle(wb);
        XSSFCellStyle redBold     = makeRedBoldStyle(wb);
        XSSFCellStyle normal      = makeNormalStyle(wb);

        // Read CSV to get original columns
        List<String[]> csvRows = readScoreSheetCsv();
        if (csvRows.isEmpty()) return;

        String[] headerRow = csvRows.get(0);
        int eolColIdx = findEolColumn(headerRow);

        // Build output header
        List<String> outHeaders = new ArrayList<>();
        for (int i = 0; i < headerRow.length; i++) {
            if (i == eolColIdx) continue; // move EOL to end
            outHeaders.add(headerRow[i]);
        }
        outHeaders.addAll(questionOrder);
        outHeaders.add("Remarks");
        outHeaders.add("Plagiarism");
        if (eolColIdx >= 0 && eolColIdx < headerRow.length) {
            outHeaders.add(headerRow[eolColIdx]);
        }

        Row hdr = sheet.createRow(0);
        for (int i = 0; i < outHeaders.size(); i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(outHeaders.get(i));
            c.setCellStyle(headerStyle);
        }

        // Data rows
        int rowIdx = 1;
        for (int csvRow = 1; csvRow < csvRows.size(); csvRow++) {
            String[] cols = csvRows.get(csvRow);
            if (cols.length < 2) continue;

            String username = cols[1].startsWith("#") ? cols[1].substring(1) : cols[1];
            Row row = sheet.createRow(rowIdx++);
            int colIdx = 0;

            // Original CSV columns (excluding EOL)
            for (int i = 0; i < cols.length; i++) {
                if (i == eolColIdx) continue;
                Cell c = row.createCell(colIdx++);
                if (i == 5 && gradedUsernames.contains(username)) {
                    // Calculated Final Grade Numerator — auto-fill
                    c.setCellValue(totals.getOrDefault(username, 0.0));
                } else {
                    c.setCellValue(cols[i]);
                }
                c.setCellStyle(normal);
            }

            // Per-question scores
            if (gradedUsernames.contains(username)) {
                Map<String, Double> qScores = perQ.getOrDefault(username, Collections.emptyMap());
                for (String q : questionOrder) {
                    Cell c = row.createCell(colIdx++);
                    c.setCellValue(qScores.getOrDefault(q, 0.0));
                    c.setCellStyle(normal);
                }
                // Remarks
                Cell remCell = row.createCell(colIdx++);
                remCell.setCellValue(remarksByStudent.getOrDefault(username, ""));
                remCell.setCellStyle(normal);
                // Plagiarism
                Cell plagCell = row.createCell(colIdx++);
                String plagNote = plagiarismNotes.getOrDefault(username, "");
                plagCell.setCellValue(plagNote);
                plagCell.setCellStyle(plagNote.isEmpty() ? normal : redBold);
            } else {
                // Missing submission
                for (String ignored : questionOrder) row.createCell(colIdx++).setCellStyle(normal);
                Cell remCell = row.createCell(colIdx++);
                remCell.setCellValue("Missing submission - refer to Anomalies tab");
                remCell.setCellStyle(normal);
                row.createCell(colIdx++).setCellStyle(normal);
            }

            // EOL at end
            if (eolColIdx >= 0 && eolColIdx < cols.length) {
                Cell c = row.createCell(colIdx);
                c.setCellValue(cols[eolColIdx]);
                c.setCellStyle(normal);
            }
        }

        autoSizeColumns(sheet, outHeaders.size());
    }

    // =========================================================================
    // SHEET 2 — Anomalies
    // =========================================================================

    private void buildAnomalySheet(
            XSSFWorkbook wb,
            Map<String, List<GradingResult>> resultsByStudent,
            Map<String, String> anomalyRemarks,
            List<Student> allStudents,
            List<String> questionOrder,
            Map<String, Map<String, Double>> perQ,
            double totalMaxScore) {

        XSSFSheet sheet = wb.createSheet("Anomalies");
        XSSFCellStyle headerStyle = makeHeaderStyle(wb);
        XSSFCellStyle normal      = makeNormalStyle(wb);

        // Header
        List<String> headers = new ArrayList<>();
        headers.add("FolderName");
        headers.add("Calculated Final Grade Numerator");
        headers.add("Calculated Final Grade Denominator");
        headers.addAll(questionOrder);
        headers.add("Remarks");
        headers.add("Anomaly Reason");

        Row hdr = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(headers.get(i));
            c.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (Student s : allStudents) {
            if (!s.isAnomaly()) continue;
            String id = s.getId();
            Row row = sheet.createRow(rowIdx++);
            int col = 0;

            Map<String, Double> qScores = perQ.getOrDefault(id, Collections.emptyMap());
            double total = qScores.values().stream().mapToDouble(Double::doubleValue).sum();

            row.createCell(col++).setCellValue(id);
            row.createCell(col++).setCellValue(total);
            row.createCell(col++).setCellValue(totalMaxScore);
            for (String q : questionOrder) {
                row.createCell(col++).setCellValue(qScores.getOrDefault(q, 0.0));
            }
            row.createCell(col++).setCellValue(anomalyRemarks.getOrDefault(id, ""));
            row.createCell(col).setCellValue("Folder not renamed; No headers found in any submission file");
        }

        autoSizeColumns(sheet, headers.size());
    }

    // =========================================================================
    // SHEET 3 — Dashboard
    // =========================================================================

    private void buildDashboardSheet(
            XSSFWorkbook wb,
            Map<String, List<GradingResult>> resultsByStudent,
            List<String> questionOrder,
            Map<String, Double> maxScores,
            double totalMaxScore) {

        XSSFSheet sheet = wb.createSheet("Dashboard");
        XSSFCellStyle titleStyle  = makeTitleStyle(wb);
        XSSFCellStyle labelStyle  = makeLabelStyle(wb);
        XSSFCellStyle valueStyle  = makeValueStyle(wb);

        List<Double> totals = resultsByStudent.values().stream()
                .map(results -> results.stream().mapToDouble(GradingResult::getScore).sum())
                .collect(Collectors.toList());

        int n = totals.size();
        double avg = totals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double median = calcMedian(totals);
        double stdDev = calcStdDev(totals, avg);
        double highest = totals.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double lowest  = totals.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        long passed = totals.stream().filter(s -> totalMaxScore > 0 && s / totalMaxScore >= 0.5).count();
        long failed = n - passed;

        Row titleRow = sheet.createRow(0);
        Cell title = titleRow.createCell(0);
        title.setCellValue("IS442 Grading Dashboard");
        title.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

        String[][] metrics = {
                {"Total Students",    String.valueOf(n)},
                {"Average Score",     String.format("%.2f / %.2f", avg, totalMaxScore)},
                {"Median Score",      String.format("%.2f", median)},
                {"Std Deviation",     String.format("%.2f", stdDev)},
                {"Highest Score",     String.format("%.2f", highest)},
                {"Lowest Score",      String.format("%.2f", lowest)},
                {"Pass Rate",         totalMaxScore > 0 ? String.format("%.1f%%", 100.0 * passed / n) : "N/A"},
                {"Fail Rate",         totalMaxScore > 0 ? String.format("%.1f%%", 100.0 * failed / n) : "N/A"},
                {"Questions Graded",  String.valueOf(questionOrder.size())},
                {"Total Max Score",   String.format("%.2f", totalMaxScore)},
        };

        for (int i = 0; i < metrics.length; i++) {
            Row row = sheet.createRow(i + 2);
            Cell lbl = row.createCell(0); lbl.setCellValue(metrics[i][0]); lbl.setCellStyle(labelStyle);
            Cell val = row.createCell(1); val.setCellValue(metrics[i][1]); val.setCellStyle(valueStyle);
        }

        sheet.setColumnWidth(0, 7000);
        sheet.setColumnWidth(1, 6000);
    }

    // =========================================================================
    // SHEET 4 — Grade Distribution
    // =========================================================================

    private void buildGradeDistributionSheet(
            XSSFWorkbook wb,
            Map<String, List<GradingResult>> resultsByStudent,
            double totalMaxScore) {

        XSSFSheet sheet = wb.createSheet("Grade Distribution");
        XSSFCellStyle headerStyle = makeHeaderStyle(wb);
        XSSFCellStyle normal      = makeNormalStyle(wb);

        Row hdr = sheet.createRow(0);
        String[] cols = {"Grade", "Min %", "Max %", "Count", "Percentage"};
        for (int i = 0; i < cols.length; i++) {
            Cell c = hdr.createCell(i); c.setCellValue(cols[i]); c.setCellStyle(headerStyle);
        }

        List<Double> totals = resultsByStudent.values().stream()
                .map(r -> r.stream().mapToDouble(GradingResult::getScore).sum())
                .collect(Collectors.toList());
        int n = totals.size();

        String[][] grades = {{"A","80","100"},{"B","70","79"},{"C","60","69"},{"D","50","59"},{"F","0","49"}};
        short[] colors = {IndexedColors.LIGHT_GREEN.getIndex(), IndexedColors.LIGHT_YELLOW.getIndex(),
                IndexedColors.LIGHT_YELLOW.getIndex(), IndexedColors.LIGHT_ORANGE.getIndex(),
                IndexedColors.ROSE.getIndex()};

        for (int i = 0; i < grades.length; i++) {
            double lo = Double.parseDouble(grades[i][1]);
            double hi = Double.parseDouble(grades[i][2]);
            long count = totals.stream().filter(s -> {
                double pct = totalMaxScore > 0 ? s / totalMaxScore * 100 : 0;
                return pct >= lo && pct <= hi;
            }).count();

            XSSFCellStyle rowStyle = makeColorStyle(wb, colors[i]);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(grades[i][0]);
            row.createCell(1).setCellValue(lo);
            row.createCell(2).setCellValue(hi);
            row.createCell(3).setCellValue(count);
            row.createCell(4).setCellValue(n > 0 ? String.format("%.1f%%", 100.0 * count / n) : "0%");
            for (int c = 0; c < 5; c++) row.getCell(c).setCellStyle(rowStyle);
        }

        autoSizeColumns(sheet, 5);
    }

    // =========================================================================
    // SHEET 5 — Question Analysis
    // =========================================================================

    private void buildQuestionAnalysisSheet(
            XSSFWorkbook wb,
            Map<String, List<GradingResult>> resultsByStudent,
            List<String> questionOrder,
            Map<String, Double> maxScores) {

        XSSFSheet sheet = wb.createSheet("Question Analysis");
        XSSFCellStyle headerStyle = makeHeaderStyle(wb);
        XSSFCellStyle normal      = makeNormalStyle(wb);

        Row hdr = sheet.createRow(0);
        String[] cols = {"Question", "Max Score", "Avg Score", "Pass Rate", "Perfect Rate", "Difficulty"};
        for (int i = 0; i < cols.length; i++) {
            Cell c = hdr.createCell(i); c.setCellValue(cols[i]); c.setCellStyle(headerStyle);
        }

        // group results by question
        Map<String, List<GradingResult>> byQ = new LinkedHashMap<>();
        for (String q : questionOrder) byQ.put(q, new ArrayList<>());
        for (List<GradingResult> studentResults : resultsByStudent.values()) {
            for (GradingResult r : studentResults) {
                byQ.computeIfAbsent(r.getQuestionId(), k -> new ArrayList<>()).add(r);
            }
        }

        int rowIdx = 1;
        for (String q : questionOrder) {
            List<GradingResult> qResults = byQ.getOrDefault(q, Collections.emptyList());
            double maxScore = maxScores.getOrDefault(q, 0.0);
            double avg = qResults.stream().mapToDouble(GradingResult::getScore).average().orElse(0);
            long total   = qResults.size();
long passed  = qResults.stream().filter(r -> r.isPerfect() || r.isPartial()).count();
long perfect = qResults.stream().filter(r -> r.isPerfect()).count();;

            double passRate = total > 0 ? 100.0 * passed / total : 0;
            String difficulty;
            if (passRate >= 80) difficulty = "Easy";
            else if (passRate >= 60) difficulty = "Moderate";
            else if (passRate >= 40) difficulty = "Hard";
            else difficulty = "Very Hard";

            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(q);
            row.createCell(1).setCellValue(maxScore);
            row.createCell(2).setCellValue(String.format("%.2f", avg));
            row.createCell(3).setCellValue(String.format("%.1f%%", passRate));
            row.createCell(4).setCellValue(total > 0 ? String.format("%.1f%%", 100.0 * perfect / total) : "0%");
            row.createCell(5).setCellValue(difficulty);
            for (int c = 0; c < 6; c++) row.getCell(c).setCellStyle(normal);
        }

        autoSizeColumns(sheet, 6);
    }

    // =========================================================================
    // SHEET 6 — Student Ranking
    // =========================================================================

    private void buildStudentRankingSheet(
            XSSFWorkbook wb,
            Map<String, List<GradingResult>> resultsByStudent,
            List<String> questionOrder,
            Map<String, Double> totalMap,
            double totalMaxScore) {

        XSSFSheet sheet = wb.createSheet("Student Ranking");
        XSSFCellStyle headerStyle = makeHeaderStyle(wb);
        XSSFCellStyle normal      = makeNormalStyle(wb);

        Row hdr = sheet.createRow(0);
        String[] cols = {"Rank", "Username", "Total Score", "Percentage", "Grade", "Status", "Percentile"};
        for (int i = 0; i < cols.length; i++) {
            Cell c = hdr.createCell(i); c.setCellValue(cols[i]); c.setCellStyle(headerStyle);
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(totalMap.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        int n = sorted.size();

        for (int i = 0; i < sorted.size(); i++) {
            String username = sorted.get(i).getKey();
            double score    = sorted.get(i).getValue();
            double pct      = totalMaxScore > 0 ? score / totalMaxScore * 100 : 0;
            String grade    = toGrade(pct);
            String status   = pct >= 50 ? "PASS" : "FAIL";
            double percentile = n > 1 ? 100.0 * (n - 1 - i) / (n - 1) : 100.0;

            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(i + 1);
            row.createCell(1).setCellValue(username);
            row.createCell(2).setCellValue(score);
            row.createCell(3).setCellValue(String.format("%.1f%%", pct));
            row.createCell(4).setCellValue(grade);
            row.createCell(5).setCellValue(status);
            row.createCell(6).setCellValue(String.format("%.1f", percentile));
            for (int c = 0; c < 7; c++) row.getCell(c).setCellStyle(normal);
        }

        autoSizeColumns(sheet, 7);
    }

    // =========================================================================
    // SHEET 7 — Performance Matrix
    // =========================================================================

    private void buildPerformanceMatrixSheet(
            XSSFWorkbook wb,
            Map<String, List<GradingResult>> resultsByStudent,
            List<String> questionOrder,
            Map<String, Map<String, Double>> perQ,
            Map<String, Double> maxScores) {

        XSSFSheet sheet = wb.createSheet("Performance Matrix");
        XSSFCellStyle headerStyle  = makeHeaderStyle(wb);
        XSSFCellStyle greenStyle   = makeColorStyle(wb, IndexedColors.LIGHT_GREEN.getIndex());
        XSSFCellStyle yellowStyle  = makeColorStyle(wb, IndexedColors.LIGHT_YELLOW.getIndex());
        XSSFCellStyle redStyle     = makeColorStyle(wb, IndexedColors.ROSE.getIndex());
        XSSFCellStyle normal       = makeNormalStyle(wb);

        // Header row
        Row hdr = sheet.createRow(0);
        hdr.createCell(0).setCellValue("Username");
        hdr.getCell(0).setCellStyle(headerStyle);
        for (int i = 0; i < questionOrder.size(); i++) {
            Cell c = hdr.createCell(i + 1);
            c.setCellValue(questionOrder.get(i));
            c.setCellStyle(headerStyle);
        }
        hdr.createCell(questionOrder.size() + 1).setCellValue("Total");
        hdr.getCell(questionOrder.size() + 1).setCellStyle(headerStyle);

        List<String> students = new ArrayList<>(resultsByStudent.keySet());
        Collections.sort(students);

        int rowIdx = 1;
        for (String student : students) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(student);
            row.getCell(0).setCellStyle(normal);
            double total = 0;
            Map<String, Double> qScores = perQ.getOrDefault(student, Collections.emptyMap());
            for (int i = 0; i < questionOrder.size(); i++) {
                String q = questionOrder.get(i);
                double score = qScores.getOrDefault(q, 0.0);
                double max   = maxScores.getOrDefault(q, 1.0);
                total += score;
                Cell c = row.createCell(i + 1);
                c.setCellValue(score);
                if (score >= max) c.setCellStyle(greenStyle);
                else if (score > 0) c.setCellStyle(yellowStyle);
                else c.setCellStyle(redStyle);
            }
            Cell totalCell = row.createCell(questionOrder.size() + 1);
            totalCell.setCellValue(total);
            totalCell.setCellStyle(normal);
        }

        autoSizeColumns(sheet, questionOrder.size() + 2);
    }

    // =========================================================================
    // Helper — data builders
    // =========================================================================

    private List<String> buildQuestionOrder(Map<String, List<GradingResult>> resultsByStudent) {
        Set<String> ids = new LinkedHashSet<>();
        for (List<GradingResult> results : resultsByStudent.values()) {
            for (GradingResult r : results) ids.add(r.getQuestionId());
        }
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(this::naturalCompare);
        return sorted;
    }

    private Map<String, Double> buildTotalScoreMap(Map<String, List<GradingResult>> byStudent) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (Map.Entry<String, List<GradingResult>> e : byStudent.entrySet()) {
            double total = e.getValue().stream().mapToDouble(GradingResult::getScore).sum();
            map.put(e.getKey(), total);
        }
        return map;
    }

    private Map<String, Map<String, Double>> buildPerQuestionMap(Map<String, List<GradingResult>> byStudent) {
        Map<String, Map<String, Double>> map = new LinkedHashMap<>();
        for (Map.Entry<String, List<GradingResult>> e : byStudent.entrySet()) {
            Map<String, Double> qMap = new LinkedHashMap<>();
            for (GradingResult r : e.getValue()) qMap.put(r.getQuestionId(), r.getScore());
            map.put(e.getKey(), qMap);
        }
        return map;
    }

    private Map<String, Double> inferMaxScores(Map<String, List<GradingResult>> byStudent) {
        Map<String, Double> maxScores = new LinkedHashMap<>();
        for (List<GradingResult> results : byStudent.values()) {
            for (GradingResult r : results) {
                maxScores.merge(r.getQuestionId(), r.getMaxScore(), Math::max);
            }
        }
        return maxScores;
    }

    private List<String[]> readScoreSheetCsv() throws IOException {
        Path csv = PathConfig.CSV_SCORESHEET;
        List<String[]> rows = new ArrayList<>();
        if (!Files.exists(csv)) return rows;
        for (String line : Files.readAllLines(csv)) {
            rows.add(line.split(",", -1));
        }
        return rows;
    }

    private int findEolColumn(String[] headerRow) {
        for (int i = 0; i < headerRow.length; i++) {
            if (headerRow[i].trim().toLowerCase().contains("end-of-line")) return i;
        }
        return -1;
    }

    private int naturalCompare(String a, String b) {
        // Split "Q10b" → ["Q",10,"b"] for natural ordering
        String[] pa = splitNatural(a), pb = splitNatural(b);
        for (int i = 0; i < Math.min(pa.length, pb.length); i++) {
            int cmp;
            try { cmp = Integer.compare(Integer.parseInt(pa[i]), Integer.parseInt(pb[i])); }
            catch (NumberFormatException e) { cmp = pa[i].compareTo(pb[i]); }
            if (cmp != 0) return cmp;
        }
        return Integer.compare(pa.length, pb.length);
    }

    private String[] splitNatural(String s) {
        return s.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
    }

    // =========================================================================
    // Helper — statistics math
    // =========================================================================

    private double calcMedian(List<Double> values) {
        if (values.isEmpty()) return 0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 0 ? (sorted.get(n/2 - 1) + sorted.get(n/2)) / 2.0 : sorted.get(n/2);
    }

    private double calcStdDev(List<Double> values, double mean) {
        if (values.size() < 2) return 0;
        double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        return Math.sqrt(variance);
    }

    private String toGrade(double pct) {
        if (pct >= 80) return "A";
        if (pct >= 70) return "B";
        if (pct >= 60) return "C";
        if (pct >= 50) return "D";
        return "F";
    }

    // =========================================================================
    // Helper — POI styles
    // =========================================================================

    private XSSFCellStyle makeHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont(); f.setBold(true); s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private XSSFCellStyle makeNormalStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private XSSFCellStyle makeRedBoldStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = wb.createFont(); f.setBold(true); s.setFont(f);
        return s;
    }

    private XSSFCellStyle makeTitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont(); f.setBold(true); f.setFontHeightInPoints((short) 14); s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont fWhite = wb.createFont(); fWhite.setColor(IndexedColors.WHITE.getIndex()); fWhite.setBold(true);
        fWhite.setFontHeightInPoints((short) 14); s.setFont(fWhite);
        return s;
    }

    private XSSFCellStyle makeLabelStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont(); f.setBold(true); s.setFont(f);
        s.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private XSSFCellStyle makeValueStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private XSSFCellStyle makeColorStyle(XSSFWorkbook wb, short color) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(color);
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private void autoSizeColumns(XSSFSheet sheet, int numCols) {
        for (int i = 0; i < numCols; i++) {
            sheet.autoSizeColumn(i);
            // cap at ~15000 units to prevent extremely wide columns
            if (sheet.getColumnWidth(i) > 15000) sheet.setColumnWidth(i, 15000);
        }
    }
}