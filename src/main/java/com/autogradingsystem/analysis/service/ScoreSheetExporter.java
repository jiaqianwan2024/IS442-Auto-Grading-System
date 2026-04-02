package com.autogradingsystem.analysis.service;

import com.autogradingsystem.model.GradingResult;
import com.autogradingsystem.model.Student;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Exports a single combined XLSX report containing:
 *   Sheet 1  — Score Sheet          (identified students)
 *   Sheet 2  — Anomalies            (unidentifiable submissions)
 *   Sheet 3  — Dashboard            (class-level statistics)
 *   Sheet 4  — Grade Distribution
 *   Sheet 5  — Question Analysis
 *   Sheet 6  — Student Ranking
 *   Sheet 7  — Performance Matrix
 *
 * Output: IS442-ScoreSheet-Updated.xlsx
 *
 * Sheets 3-7 are delegated to StatisticsReportExporter.appendStatsSheets()
 * on the same XSSFWorkbook, so there is exactly ONE output file.
 * The separate IS442-Statistics.xlsx is no longer produced.
 */
public class ScoreSheetExporter {

    // ── PATH FIELDS ──────────────────────────────────────────────────────────

    private final Path csvScoresheet;
    private final Path outputReports;
    private final Path inputTesters;

    // ── CONSTRUCTORS ─────────────────────────────────────────────────────────

    /** Path-aware — multi-assessment support (full). */
    public ScoreSheetExporter(Path csvScoresheet, Path outputReports, Path inputTesters) {
        this.csvScoresheet = csvScoresheet;
        this.outputReports = outputReports;
        this.inputTesters  = inputTesters;
    }

    // ── PATH RESOLUTION ──────────────────────────────────────────────────────

    private Path resolveCsvScoresheet() {
        return csvScoresheet.toAbsolutePath();
    }

    private Path resolveInputTesters() {
        return inputTesters.toAbsolutePath();
    }

    private Path resolveOutputDir() {
        return outputReports.toAbsolutePath();
    }

    // ── PUBLIC API ────────────────────────────────────────────────────────────

    /** Backward-compatible overload — no plagiarism notes. */
    public Path export(
            Map<String, List<GradingResult>> resultsByStudent,
            Map<String, String> remarksByStudent,
            Map<String, String> anomalyRemarks,
            List<Student> allStudents) throws IOException {
        return export(resultsByStudent, remarksByStudent, anomalyRemarks, allStudents,
                      Collections.emptyMap());
    }

    /**
     * Overload — includes plagiarism notes (v3.3+) but no penalty totals.
     * Delegates to the full 7-param overload with empty penalty maps.
     */
    public Path export(
            Map<String, List<GradingResult>> resultsByStudent,
            Map<String, String> remarksByStudent,
            Map<String, String> anomalyRemarks,
            List<Student> allStudents,
            Map<String, String> plagiarismNotes) throws IOException {
        return export(resultsByStudent, remarksByStudent, anomalyRemarks, allStudents,
                      plagiarismNotes, Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * Full overload — includes plagiarism notes, penalty-adjusted totals, and penalty remarks (v3.5+).
     *
     * @param penaltyTotals  studentId → final score after all penalty deductions
     * @param penaltyRemarks studentId → human-readable breakdown (e.g. "Q1: Compilation failure (zero marks)")
     */
    public Path export(
            Map<String, List<GradingResult>> resultsByStudent,
            Map<String, String> remarksByStudent,
            Map<String, String> anomalyRemarks,
            List<Student> allStudents,
            Map<String, String> plagiarismNotes,
            Map<String, Double> penaltyTotals,
            Map<String, String> penaltyRemarks) throws IOException {
        return export(resultsByStudent, remarksByStudent, anomalyRemarks, allStudents,
                      plagiarismNotes, penaltyTotals, penaltyRemarks, Collections.emptyMap());
    }

    public Path export(
            Map<String, List<GradingResult>> resultsByStudent,
            Map<String, String> remarksByStudent,
            Map<String, String> anomalyRemarks,
            List<Student> allStudents,
            Map<String, String> plagiarismNotes,
            Map<String, Double> penaltyTotals,
            Map<String, String> penaltyRemarks,
            Map<String, Map<String, Double>> penaltyQScores) throws IOException {

        Path outputDir  = resolveOutputDir();
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("IS442-ScoreSheet-Updated.xlsx");

        List<String> questionOrder = buildQuestionOrder(resultsByStudent);

        Map<String, Double>              totals    = new LinkedHashMap<>();
        Map<String, Double>              rawTotals = new LinkedHashMap<>();
        Map<String, Map<String, Double>> qScoreMap = new LinkedHashMap<>();
        buildScoreMaps(resultsByStudent, totals, rawTotals, qScoreMap, penaltyTotals);

        Map<String, Double> maxScores = new LinkedHashMap<>();
        for (Map.Entry<String, List<GradingResult>> entry : resultsByStudent.entrySet())
            for (GradingResult r : entry.getValue())
                maxScores.computeIfAbsent(r.getTask().getQuestionId(),
                    k -> ScoreAnalyzer.getMaxScoreFromTester(k, resolveInputTesters()));

        Set<String> gradedUsernames = totals.keySet();
        double totalMaxScore = maxScores.values().stream().mapToDouble(Double::doubleValue).sum();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            // ── Sheets 1 & 2: Score Sheet + Anomalies ────────────────────────
            buildMainSheet(workbook, resultsByStudent, remarksByStudent, allStudents,
                           plagiarismNotes, questionOrder, totals, rawTotals, qScoreMap,
                           penaltyRemarks, totalMaxScore, penaltyQScores);

            buildAnomalySheet(workbook, resultsByStudent, anomalyRemarks, allStudents,
                              questionOrder, qScoreMap, totalMaxScore);

            // ── Sheets 3-7: Statistics (Dashboard, Grade Dist, Q Analysis, Ranking, Matrix)
            StatisticsReportExporter statsExporter =
                new StatisticsReportExporter(outputReports, inputTesters);
            statsExporter.appendStatsSheets(workbook, resultsByStudent);

            try (OutputStream os = Files.newOutputStream(outputFile)) {
                workbook.write(os);
            }
        }

        // CSV sidecar (used by status checks and legacy download routes)
        exportCsv(outputDir, questionOrder, totals, rawTotals, qScoreMap, remarksByStudent,
                  plagiarismNotes, penaltyRemarks, gradedUsernames, penaltyQScores);

        return outputFile;
    }

    // ── CSV EXPORT ────────────────────────────────────────────────────────────

    private void exportCsv(Path outputDir,
                            List<String> questionOrder,
                            Map<String, Double> totals,
                            Map<String, Double> rawTotals,
                            Map<String, Map<String, Double>> qScoreMap,
                            Map<String, String> remarksByStudent,
                            Map<String, String> plagiarismNotes,
                            Map<String, String> penaltyRemarks,
                            Set<String> gradedUsernames,
                            Map<String, Map<String, Double>> penaltyQScores) throws IOException {

        Path csvOut = outputDir.resolve("IS442-ScoreSheet-Updated.csv");
        StringBuilder sb = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(resolveCsvScoresheet()), StandardCharsets.UTF_8))) {

            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader && line.startsWith("\uFEFF")) line = line.substring(1);
                String[] cols = line.split(",", -1);

                if (isHeader) {
                    StringBuilder headerRow = new StringBuilder();
                    for (int c = 0; c < cols.length; c++) {
                        if (c == COL_EOL) continue;
                        if (headerRow.length() > 0) headerRow.append(",");
                        headerRow.append(escapeCsv(cols[c].trim()));
                    }
                    // Section 1 — raw per-question scores + grading remarks
                    for (String qid : questionOrder) {
                        headerRow.append(",").append(escapeCsv(qid));
                    }
                    headerRow.append(",Remarks");
                    // Section 2 — penalty-adjusted scores + penalty remarks
                    headerRow.append(",Calculated Final Grade Numerator,Calculated Final Grade Denominator");
                    for (String qid : questionOrder) {
                        headerRow.append(",").append(escapeCsv(qid));
                    }
                    headerRow.append(",Penalty Remarks,Plagiarism");
                    headerRow.append(",").append(cols.length > COL_EOL ? escapeCsv(cols[COL_EOL].trim()) : "#");
                    sb.append(headerRow).append("\n");
                    isHeader = false;
                    continue;
                }

                String eolValue = cols.length > COL_EOL ? cols[COL_EOL].trim() : "#";
                String rawCol   = cols.length > COL_USERNAME ? cols[COL_USERNAME].trim() : "";
                String username = (rawCol.startsWith("#") ? rawCol.substring(1) : rawCol).trim();
                String denominatorValue = cols.length > COL_DENOMINATOR ? cols[COL_DENOMINATOR].trim() : "";

                // Section 1: show raw score in the Numerator column
                if (rawTotals.containsKey(username)) {
                    cols[COL_NUMERATOR] = fmtNum(rawTotals.get(username));
                }

                StringBuilder dataRow = new StringBuilder();
                for (int c = 0; c < cols.length; c++) {
                    if (c == COL_EOL) continue;
                    if (dataRow.length() > 0) dataRow.append(",");
                    dataRow.append(escapeCsv(cols[c].trim()));
                }

                Map<String, Double> qScores = qScoreMap.getOrDefault(username, Collections.emptyMap());
                // Section 1: raw per-Q scores
                for (String qid : questionOrder) {
                    dataRow.append(",").append(fmtNum(qScores.getOrDefault(qid, 0.0)));
                }
                String remarks = gradedUsernames.contains(username)
                        ? remarksByStudent.getOrDefault(username, "")
                        : "Missing submission";
                dataRow.append(",").append(escapeCsv(remarks));

                // Section 2: penalty-adjusted numerator + same denominator
                dataRow.append(",").append(fmtNum(totals.getOrDefault(username, 0.0)));
                dataRow.append(",").append(escapeCsv(denominatorValue));
                // Section 2: penalty-adjusted per-Q scores
                Map<String, Double> penaltyQs = penaltyQScores.getOrDefault(username, Collections.emptyMap());
                for (String qid : questionOrder) {
                    double qVal = penaltyQs.containsKey(qid) ? penaltyQs.get(qid) : qScores.getOrDefault(qid, 0.0);
                    dataRow.append(",").append(fmtNum(qVal));
                }
                String penaltyNote = penaltyRemarks.getOrDefault(username, "No penalty");
                dataRow.append(",").append(escapeCsv(penaltyNote));
                dataRow.append(",").append(escapeCsv(plagiarismNotes.getOrDefault(username, "")));
                dataRow.append(",").append(escapeCsv(eolValue));
                sb.append(dataRow).append("\n");
            }
        }

        Files.writeString(csvOut, sb.toString(), StandardCharsets.UTF_8);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ── SHEET 1: Score Sheet ──────────────────────────────────────────────────

    private static final int COL_USERNAME    = 1;
    private static final int COL_NUMERATOR   = 5;
    private static final int COL_DENOMINATOR = 6;
    private static final int COL_EOL         = 7;

    private void buildMainSheet(
            XSSFWorkbook wb,
            Map<String, List<GradingResult>> resultsByStudent,
            Map<String, String> remarksByStudent,
            List<Student> allStudents,
            Map<String, String> plagiarismNotes,
            List<String> questionOrder,
            Map<String, Double> totals,
            Map<String, Double> rawTotals,
            Map<String, Map<String, Double>> perQ,
            Map<String, String> penaltyRemarks,
            double totalMaxScore,
            Map<String, Map<String, Double>> penaltyQScores) throws IOException {

        XSSFSheet sheet = wb.createSheet("Score Sheet");
        Set<String> gradedUsernames = resultsByStudent.keySet();

        XSSFCellStyle headerStyle = makeHeaderStyle(wb);
        XSSFCellStyle normal      = makeNormalStyle(wb);
        XSSFCellStyle redBold     = makeColorStyle(wb, IndexedColors.ROSE.getIndex());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(resolveCsvScoresheet()), StandardCharsets.UTF_8))) {

            String line;
            boolean isHeader = true;
            int rowIdx = 0;
            List<String> outHeaders = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                if (rowIdx == 0 && line.startsWith("\uFEFF")) line = line.substring(1);
                String[] cols = line.split(",", -1);
                Row row = sheet.createRow(rowIdx++);

                if (isHeader) {
                    outHeaders.clear();
                    int cellIdx = 0;
                    for (int c = 0; c < cols.length; c++) {
                        if (c == COL_EOL) continue;
                        String h = cols[c].trim();
                        Cell cell = row.createCell(cellIdx++);
                        cell.setCellValue(h);
                        cell.setCellStyle(headerStyle);
                        outHeaders.add(h);
                    }
                    // Section 1 — raw per-question scores + grading remarks
                    for (String qid : questionOrder) {
                        Cell cell = row.createCell(cellIdx++);
                        cell.setCellValue(qid); cell.setCellStyle(headerStyle);
                        outHeaders.add(qid);
                    }
                    Cell rh = row.createCell(cellIdx++);
                    rh.setCellValue("Remarks"); rh.setCellStyle(headerStyle);
                    outHeaders.add("Remarks");

                    // Section 2 — penalty-adjusted numerator + denominator
                    Cell numH = row.createCell(cellIdx++);
                    numH.setCellValue("Calculated Final Grade Numerator"); numH.setCellStyle(headerStyle);
                    outHeaders.add("Calculated Final Grade Numerator");
                    Cell denH = row.createCell(cellIdx++);
                    denH.setCellValue("Calculated Final Grade Denominator"); denH.setCellStyle(headerStyle);
                    outHeaders.add("Calculated Final Grade Denominator");
                    // Section 2 — per-question scores (penalty-adjusted per Q when implemented)
                    for (String qid : questionOrder) {
                        Cell cell = row.createCell(cellIdx++);
                        cell.setCellValue(qid); cell.setCellStyle(headerStyle);
                        outHeaders.add(qid);
                    }
                    Cell prH = row.createCell(cellIdx++);
                    prH.setCellValue("Penalty Remarks"); prH.setCellStyle(headerStyle);
                    outHeaders.add("Penalty Remarks");

                    Cell ph = row.createCell(cellIdx++);
                    ph.setCellValue("Plagiarism"); ph.setCellStyle(headerStyle);
                    outHeaders.add("Plagiarism");

                    Cell eh = row.createCell(cellIdx);
                    eh.setCellValue(cols.length > COL_EOL ? cols[COL_EOL].trim() : "#");
                    eh.setCellStyle(headerStyle);
                    outHeaders.add(cols.length > COL_EOL ? cols[COL_EOL].trim() : "#");
                    isHeader = false;
                    continue;
                }

                String eolValue      = cols.length > COL_EOL         ? cols[COL_EOL].trim()         : "#";
                String rawCol        = cols.length > COL_USERNAME     ? cols[COL_USERNAME].trim()    : "";
                String username      = (rawCol.startsWith("#") ? rawCol.substring(1) : rawCol).trim();
                String denomValue    = cols.length > COL_DENOMINATOR  ? cols[COL_DENOMINATOR].trim() : "";

                // Section 1: show raw score in the Numerator column
                if (rawTotals.containsKey(username)) {
                    cols[COL_NUMERATOR] = fmtNum(rawTotals.get(username));
                }

                int colIdx = 0;
                for (int c = 0; c < cols.length; c++) {
                    if (c == COL_EOL) continue;
                    row.createCell(colIdx++).setCellValue(cols[c].trim());
                }

                if (gradedUsernames.contains(username)) {
                    Map<String, Double> qScores = perQ.getOrDefault(username, Collections.emptyMap());

                    // Section 1: raw per-Q scores
                    for (String qid : questionOrder) {
                        row.createCell(colIdx++).setCellValue(qScores.getOrDefault(qid, 0.0));
                    }
                    Cell remCell = row.createCell(colIdx++);
                    remCell.setCellValue(remarksByStudent.getOrDefault(username, ""));
                    remCell.setCellStyle(normal);

                    // Section 2: penalty-adjusted numerator + original denominator
                    row.createCell(colIdx++).setCellValue(totals.getOrDefault(username, 0.0));
                    row.createCell(colIdx++).setCellValue(denomValue);
                    // Section 2: penalty-adjusted per-Q scores
                    Map<String, Double> penaltyQs = penaltyQScores.getOrDefault(username, Collections.emptyMap());
                    for (String qid : questionOrder) {
                        double qVal = penaltyQs.containsKey(qid) ? penaltyQs.get(qid) : qScores.getOrDefault(qid, 0.0);
                        row.createCell(colIdx++).setCellValue(qVal);
                    }
                    Cell prCell = row.createCell(colIdx++);
                    String penaltyNote = penaltyRemarks.getOrDefault(username, "No penalty");
                    prCell.setCellValue(penaltyNote);
                    prCell.setCellStyle("No penalty".equals(penaltyNote) ? normal : redBold);

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
                    row.createCell(colIdx++).setCellStyle(normal); // Section 2: Numerator
                    row.createCell(colIdx++).setCellStyle(normal); // Section 2: Denominator
                    for (String ignored : questionOrder) row.createCell(colIdx++).setCellStyle(normal);
                    Cell prCell = row.createCell(colIdx++);
                    prCell.setCellValue("No penalty"); prCell.setCellStyle(normal);
                    row.createCell(colIdx++).setCellStyle(normal); // Plagiarism
                }

                row.createCell(colIdx).setCellValue(eolValue);
            }

            autoSizeColumns(sheet, outHeaders.size());
        }
    }

    // ── SHEET 2: Anomalies ────────────────────────────────────────────────────

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
            String id  = s.getId();
            Row row    = sheet.createRow(rowIdx++);
            int col    = 0;

            Map<String, Double> qScores = perQ.getOrDefault(id, Collections.emptyMap());
            double total = qScores.values().stream().mapToDouble(Double::doubleValue).sum();

            row.createCell(col++).setCellValue(s.getRawFolderName() != null ? s.getRawFolderName() : id);
            row.createCell(col++).setCellValue(total);
            row.createCell(col++).setCellValue(totalMaxScore);

            for (String q : questionOrder) {
                row.createCell(col++).setCellValue(qScores.getOrDefault(q, 0.0));
            }
            row.createCell(col++).setCellValue(anomalyRemarks.getOrDefault(id, ""));

            StringBuilder reason = new StringBuilder("Folder not renamed");
            if (s.getMissingHeaderFiles() == null || s.getMissingHeaderFiles().isEmpty()) {
                reason.append("; No headers found in any submission file");
            } else {
                reason.append("; Missing headers in: ")
                      .append(String.join(", ", s.getMissingHeaderFiles()));
            }
            row.createCell(col).setCellValue(reason.toString());
        }

        autoSizeColumns(sheet, headers.size());
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private List<String> buildQuestionOrder(Map<String, List<GradingResult>> resultsByStudent) {
        Set<String> ids = new LinkedHashSet<>();
        for (List<GradingResult> results : resultsByStudent.values())
            for (GradingResult r : results) ids.add(r.getQuestionId());
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(ScoreSheetExporter::naturalCompare);
        return sorted;
    }

    /**
     * Populates {@code totals} and {@code qScoreMap} from raw grading results.
     * When {@code penaltyTotals} contains an entry for a student the penalty-adjusted
     * final score is used as that student's total instead of the raw question sum.
     */
    private void buildScoreMaps(Map<String, List<GradingResult>> resultsByStudent,
                                Map<String, Double> totals,
                                Map<String, Double> rawTotals,
                                Map<String, Map<String, Double>> qScoreMap,
                                Map<String, Double> penaltyTotals) {
        for (Map.Entry<String, List<GradingResult>> entry : resultsByStudent.entrySet()) {
            String student = entry.getKey();
            double rawTotal = 0.0;
            Map<String, Double> qScores = new LinkedHashMap<>();
            for (GradingResult r : entry.getValue()) {
                rawTotal += r.getScore();
                qScores.put(r.getTask().getQuestionId(), r.getScore());
            }
            rawTotals.put(student, rawTotal);
            // Prefer penalty-adjusted total when provided
            totals.put(student, penaltyTotals.containsKey(student)
                    ? penaltyTotals.get(student) : rawTotal);
            qScoreMap.put(student, qScores);
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

    // ── STYLE FACTORIES ───────────────────────────────────────────────────────

    private XSSFCellStyle makeHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)31,(byte)56,(byte)100}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private XSSFCellStyle makeNormalStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle makeColorStyle(XSSFWorkbook wb, short colorIndex) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(colorIndex);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void autoSizeColumns(XSSFSheet sheet, int count) {
        for (int i = 0; i < count; i++) {
            try { sheet.autoSizeColumn(i); } catch (Exception ignored) {}
        }
    }
}
