package com.autogradingsystem.plagiarism.service;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.plagiarism.model.PlagiarismResult;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

/**
 * PlagiarismReportExporter
 *
 * PURPOSE:
 *   Produces  IS442-Plagiarism-Report.xlsx  with two tabs:
 *
 *   Sheet 1 "Flagged Pairs"
 *     - One row per flagged pair, sorted by similarity descending
 *     - Columns: Question | Student A | Student B | Similarity% | Shared FPs | Summary
 *
 *   Sheet 2 "All Pairs"
 *     - Every pair checked (flagged + clean), grouped by question
 *     - Flagged rows are highlighted in red for quick review
 *
 * INTEGRATION:
 *   Called by PlagiarismController after PlagiarismDetector.detect() returns.
 *   Output lands in  resources/output/reports/IS442-Plagiarism-Report.xlsx
 */
public class PlagiarismReportExporter {

    private static final String OUTPUT_FILENAME = "IS442-Plagiarism-Report.xlsx";

    // Column indices
    private static final int COL_QUESTION    = 0;
    private static final int COL_STUDENT_A   = 1;
    private static final int COL_STUDENT_B   = 2;
    private static final int COL_SIMILARITY  = 3;
    private static final int COL_SHARED_FPS  = 4;
    private static final int COL_FLAGGED     = 5;
    private static final int COL_SUMMARY     = 6;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Exports the plagiarism report XLSX.
     *
     * @param allResults All PlagiarismResult objects (flagged + unflagged)
     * @return Path to the written file
     */
    public Path export(List<PlagiarismResult> allResults) throws IOException {
        Path outputDir  = PathConfig.OUTPUT_BASE.resolve("reports").toAbsolutePath();
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve(OUTPUT_FILENAME);

        List<PlagiarismResult> flagged = allResults.stream()
                .filter(PlagiarismResult::isFlagged)
                .sorted(Comparator.comparingDouble(PlagiarismResult::getSimilarityScore).reversed())
                .collect(Collectors.toList());

        List<PlagiarismResult> allSorted = allResults.stream()
                .sorted(Comparator.comparing(PlagiarismResult::getQuestionId)
                        .thenComparingDouble(r -> -r.getSimilarityScore()))
                .collect(Collectors.toList());

        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle  = buildHeaderStyle(workbook);
            CellStyle flaggedStyle = buildFlaggedStyle(workbook);
            CellStyle normalStyle  = buildNormalStyle(workbook);
            CellStyle pctStyle     = buildPercentStyle(workbook);
            CellStyle flaggedPct   = buildFlaggedPercentStyle(workbook);
            CellStyle titleStyle   = buildTitleStyle(workbook);

            buildFlaggedSheet(workbook, flagged, headerStyle, flaggedStyle, pctStyle, flaggedPct, titleStyle);
            buildAllPairsSheet(workbook, allSorted, headerStyle, flaggedStyle, normalStyle, pctStyle, flaggedPct, titleStyle);

            try (var out = Files.newOutputStream(outputFile)) {
                workbook.write(out);
            }
        }

        return outputFile;
    }

    // ── Sheet builders ────────────────────────────────────────────────────────

    private void buildFlaggedSheet(Workbook wb,
                                    List<PlagiarismResult> flagged,
                                    CellStyle headerStyle,
                                    CellStyle flaggedStyle,
                                    CellStyle pctStyle,
                                    CellStyle flaggedPct,
                                    CellStyle titleStyle) {

        Sheet sheet = wb.createSheet("Flagged Pairs");
        sheet.setColumnWidth(COL_QUESTION,   14 * 256);
        sheet.setColumnWidth(COL_STUDENT_A,  28 * 256);
        sheet.setColumnWidth(COL_STUDENT_B,  28 * 256);
        sheet.setColumnWidth(COL_SIMILARITY, 14 * 256);
        sheet.setColumnWidth(COL_SHARED_FPS, 16 * 256);
        sheet.setColumnWidth(COL_FLAGGED,    10 * 256);
        sheet.setColumnWidth(COL_SUMMARY,    70 * 256);

        // Title row
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("IS442 Plagiarism Report — Flagged Pairs  ["
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "]");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        // Header row
        Row header = sheet.createRow(1);
        writeHeader(header, headerStyle);

        if (flagged.isEmpty()) {
            Row row = sheet.createRow(2);
            Cell cell = row.createCell(0);
            cell.setCellValue("✅ No suspicious pairs detected.");
            return;
        }

        int rowIdx = 2;
        for (PlagiarismResult r : flagged) {
            Row row = sheet.createRow(rowIdx++);
            writeResultRow(row, r, flaggedStyle, flaggedPct);
        }
    }

    private void buildAllPairsSheet(Workbook wb,
                                     List<PlagiarismResult> all,
                                     CellStyle headerStyle,
                                     CellStyle flaggedStyle,
                                     CellStyle normalStyle,
                                     CellStyle pctStyle,
                                     CellStyle flaggedPct,
                                     CellStyle titleStyle) {

        Sheet sheet = wb.createSheet("All Pairs");
        sheet.setColumnWidth(COL_QUESTION,   14 * 256);
        sheet.setColumnWidth(COL_STUDENT_A,  28 * 256);
        sheet.setColumnWidth(COL_STUDENT_B,  28 * 256);
        sheet.setColumnWidth(COL_SIMILARITY, 14 * 256);
        sheet.setColumnWidth(COL_SHARED_FPS, 16 * 256);
        sheet.setColumnWidth(COL_FLAGGED,    10 * 256);
        sheet.setColumnWidth(COL_SUMMARY,    70 * 256);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("IS442 Plagiarism Report — All Pairs ("
                + all.size() + " total)  ["
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "]");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        Row header = sheet.createRow(1);
        writeHeader(header, headerStyle);

        int rowIdx = 2;
        for (PlagiarismResult r : all) {
            Row row = sheet.createRow(rowIdx++);
            if (r.isFlagged()) {
                writeResultRow(row, r, flaggedStyle, flaggedPct);
            } else {
                writeResultRow(row, r, normalStyle, pctStyle);
            }
        }
    }

    private void writeHeader(Row row, CellStyle style) {
        String[] headers = { "Question", "Student A", "Student B",
                              "Similarity", "Shared FPs", "Flagged", "Summary" };
        for (int i = 0; i < headers.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(style);
        }
    }

    private void writeResultRow(Row row, PlagiarismResult r,
                                  CellStyle baseStyle, CellStyle pctStyle) {
        // Parse shared FPs from summary string
        int sharedFPs = parseSharedFPs(r.getSummary());

        createStyledCell(row, COL_QUESTION,   r.getQuestionId(),                                baseStyle);
        createStyledCell(row, COL_STUDENT_A,  r.getStudentA(),                                   baseStyle);
        createStyledCell(row, COL_STUDENT_B,  r.getStudentB(),                                   baseStyle);
        Cell pctCell = row.createCell(COL_SIMILARITY);
        pctCell.setCellValue(r.getSimilarityScore());
        pctCell.setCellStyle(pctStyle);
        createStyledCell(row, COL_SHARED_FPS, sharedFPs,                                         baseStyle);
        createStyledCell(row, COL_FLAGGED,    r.isFlagged() ? "YES" : "no",                      baseStyle);
        createStyledCell(row, COL_SUMMARY,    r.getSummary(),                                     baseStyle);
    }

    private int parseSharedFPs(String summary) {
        try {
            int idx = summary.indexOf("shared_fingerprints=");
            if (idx < 0) return 0;
            String after = summary.substring(idx + "shared_fingerprints=".length());
            return Integer.parseInt(after.split("[^0-9]")[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    private void createStyledCell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private void createStyledCell(Row row, int col, int value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    // ── Style factories ───────────────────────────────────────────────────────

    private CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle buildFlaggedStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle buildNormalStyle(Workbook wb) {
        return wb.createCellStyle();
    }

    private CellStyle buildPercentStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("0.0%"));
        return style;
    }

    private CellStyle buildFlaggedPercentStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("0.0%"));
        return style;
    }

    private CellStyle buildTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 13);
        style.setFont(font);
        return style;
    }
}
