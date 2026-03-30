package com.autogradingsystem.analysis.service;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.model.GradingResult;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * StatisticsReportExporter
 *
 * v3.4+ — Statistics sheets are now embedded directly in IS442-ScoreSheet-Updated.xlsx
 * by {@link ScoreSheetExporter}. The standalone IS442-Statistics.xlsx is no longer produced.
 *
 * The build*() methods are package-private so ScoreSheetExporter can call them on a
 * shared XSSFWorkbook, appending sheets directly after Score Sheet + Anomalies.
 *
 * The top-level export() is retained as a no-op for backward compatibility only.
 */
@Deprecated
public class StatisticsReportExporter {

    private static final double A_MIN = 80.0;
    private static final double B_MIN = 70.0;
    private static final double C_MIN = 60.0;
    private static final double D_MIN = 50.0;
    private static final double PASS  = 50.0;

    // Colour palette (package-private so ScoreSheetExporter can reference if needed)
    static final String COL_NAVY      = "1F3864";
    static final String COL_BLUE      = "2E75B6";
    static final String COL_LIGHT_BLU = "D6E4F0";
    static final String COL_GREEN     = "E2EFDA";
    static final String COL_RED       = "FCE4D6";
    static final String COL_YELLOW    = "FFF2CC";

    // ── PATH FIELDS ──────────────────────────────────────────────────────────

    private final Path outputReports;
    private final Path inputTesters;

    public StatisticsReportExporter() {
        this.outputReports = null;
        this.inputTesters  = null;
    }

    public StatisticsReportExporter(Path outputReports, Path inputTesters) {
        this.outputReports = outputReports;
        this.inputTesters  = inputTesters;
    }

    private Path resolveInputTesters() {
        return inputTesters != null
            ? inputTesters.toAbsolutePath()
            : PathConfig.INPUT_TESTERS.toAbsolutePath();
    }

    // Shared styles — initialised by initStyles() before any build* call
    XSSFCellStyle titleStyle, sectionStyle, colHeaderStyle;
    XSSFCellStyle normalStyle, altStyle, boldStyle;
    XSSFCellStyle pctStyle, pctAltStyle;

    // ── NO-OP export() ───────────────────────────────────────────────────────

    /**
     * @deprecated Stats are now embedded in IS442-ScoreSheet-Updated.xlsx.
     *             Call ScoreSheetExporter.export() instead.
     */
    @Deprecated
    public Path export(Map<String, List<GradingResult>> resultsByStudent) throws IOException {
        System.out.println("ℹ️  StatisticsReportExporter.export() — stats are now embedded in the score sheet.");
        return null;
    }

    // ── PACKAGE-PRIVATE: append all stats sheets to an existing workbook ─────
    // Called by ScoreSheetExporter after writing Score Sheet + Anomalies tabs.

    void appendStatsSheets(XSSFWorkbook wb,
                           Map<String, List<GradingResult>> resultsByStudent) {
        try {
            List<GradingResult> allResults = resultsByStudent.values().stream()
                    .flatMap(Collection::stream).collect(Collectors.toList());

            Map<String, Double>              maxScores  = ScoreAnalyzer.inferMaxScores(allResults, resolveInputTesters());
            Map<String, List<GradingResult>> byQuestion = ScoreAnalyzer.groupByQuestion(allResults);

            List<String> qOrder = new ArrayList<>(maxScores.keySet());
            qOrder.sort(ScoreSheetExporter::naturalCompare);

            double totalMax    = maxScores.values().stream().mapToDouble(Double::doubleValue).sum();
            List<StudentRecord> ranked = buildRanked(resultsByStudent, totalMax);
            int    n           = ranked.size();
            double classAvg    = ranked.stream().mapToDouble(r -> r.total).average().orElse(0.0);
            double classAvgPct = totalMax > 0 ? classAvg / totalMax * 100.0 : 0.0;
            double median      = calcMedian(ranked.stream().mapToDouble(r -> r.total).boxed().collect(Collectors.toList()));
            double stdDev      = calcStdDev(ranked.stream().mapToDouble(r -> r.total).boxed().collect(Collectors.toList()), classAvg);
            long   cPass       = ranked.stream().filter(r -> r.pct >= PASS).count();
            long   cA = ranked.stream().filter(r -> r.pct >= A_MIN).count();
            long   cB = ranked.stream().filter(r -> r.pct >= B_MIN && r.pct < A_MIN).count();
            long   cC = ranked.stream().filter(r -> r.pct >= C_MIN && r.pct < B_MIN).count();
            long   cD = ranked.stream().filter(r -> r.pct >= D_MIN && r.pct < C_MIN).count();
            long   cF = ranked.stream().filter(r -> r.pct < D_MIN).count();

            initStyles(wb);
            buildDashboard(wb, n, totalMax, classAvg, classAvgPct, median, stdDev, cPass, ranked);
            buildGradeDist(wb, n, cA, cB, cC, cD, cF);
            buildQuestions(wb, qOrder, maxScores, byQuestion);
            buildRanking(wb, ranked, totalMax);
            buildMatrix(wb, ranked, qOrder, maxScores, totalMax, classAvg, classAvgPct, byQuestion);

        } catch (Exception e) {
            System.err.println("❌ Statistics sheet generation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── Dashboard ────────────────────────────────────────────────────────────

    void buildDashboard(XSSFWorkbook wb, int n, double totalMax,
                        double classAvg, double classAvgPct,
                        double median, double stdDev, long cPass,
                        List<StudentRecord> ranked) {
        XSSFSheet s = wb.createSheet("Dashboard");
        s.setColumnWidth(0, 10000);
        s.setColumnWidth(1, 8000);
        s.setColumnWidth(2, 5000);

        Row titleRow = s.createRow(0);
        titleRow.setHeightInPoints(36);
        XSSFCell tc = (XSSFCell) titleRow.createCell(0);
        tc.setCellValue("IS442 AUTO-GRADING SYSTEM — CLASS STATISTICS REPORT");
        tc.setCellStyle(titleStyle);
        s.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));

        Row tsRow = s.createRow(1);
        cell(tsRow, 0, "Generated: " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm:ss")), normalStyle);
        s.addMergedRegion(new CellRangeAddress(1, 1, 0, 2));

        s.createRow(2);
        sectionRow(s, 3, "CLASS DASHBOARD", 3);

        Row hdr = s.createRow(4);
        hdr.setHeightInPoints(20);
        cell(hdr, 0, "Metric",     colHeaderStyle);
        cell(hdr, 1, "Value",      colHeaderStyle);
        cell(hdr, 2, "Percentage", colHeaderStyle);

        StudentRecord top    = ranked.isEmpty() ? null : ranked.get(0);
        StudentRecord bottom = ranked.isEmpty() ? null : ranked.get(ranked.size() - 1);

        Object[][] rows = {
            {"Class Average",       fmt(classAvg) + " / " + fmt(totalMax), classAvgPct / 100.0},
            {"Median Score",        fmt(median)   + " / " + fmt(totalMax), totalMax > 0 ? median/totalMax : 0.0},
            {"Std Deviation",       String.format("%.2f", stdDev),         null},
            {"Highest Score",       top    != null ? fmt(top.total)    + "  —  " + top.username    : "N/A",
                                    top    != null ? top.pct / 100.0    : null},
            {"Lowest Score",        bottom != null ? fmt(bottom.total) + "  —  " + bottom.username : "N/A",
                                    bottom != null ? bottom.pct / 100.0 : null},
            {"Pass Rate  (>= 50%)", cPass + " out of " + n + " students",  n > 0 ? (double)cPass/n : 0.0},
            {"Fail Rate  (<  50%)", (n-cPass) + " out of " + n + " students", n > 0 ? (double)(n-cPass)/n : 0.0},
            {"Total Students",      String.valueOf(n),                     null},
            {"Max Possible Score",  fmt(totalMax),                         null},
        };

        for (int i = 0; i < rows.length; i++) {
            Row r = s.createRow(5 + i);
            r.setHeightInPoints(18);
            boolean alt = i % 2 == 1;
            cell(r, 0, (String) rows[i][0], alt ? altStyle : normalStyle);
            cell(r, 1, (String) rows[i][1], alt ? altStyle : normalStyle);
            if (rows[i][2] != null) {
                Cell pc = r.createCell(2);
                pc.setCellValue((double) rows[i][2]);
                pc.setCellStyle(alt ? pctAltStyle : pctStyle);
            } else {
                cell(r, 2, "", alt ? altStyle : normalStyle);
            }
        }
    }

    // ── Grade Distribution ───────────────────────────────────────────────────

    void buildGradeDist(XSSFWorkbook wb, int n,
                        long cA, long cB, long cC, long cD, long cF) {
        XSSFSheet s = wb.createSheet("Grade Distribution");
        for (int i = 0; i < 4; i++) s.setColumnWidth(i, 5000);

        sectionRow(s, 0, "GRADE DISTRIBUTION", 4);
        Row hdr = s.createRow(1);
        hdr.setHeightInPoints(20);
        cell(hdr, 0, "Grade",      colHeaderStyle);
        cell(hdr, 1, "Range",      colHeaderStyle);
        cell(hdr, 2, "Count",      colHeaderStyle);
        cell(hdr, 3, "Percentage", colHeaderStyle);

        Object[][] grades = {
            {"A", ">= 80%",   cA, COL_GREEN},
            {"B", "70 - 79%", cB, COL_LIGHT_BLU},
            {"C", "60 - 69%", cC, COL_YELLOW},
            {"D", "50 - 59%", cD, COL_YELLOW},
            {"F", "< 50%",    cF, COL_RED},
        };

        for (int i = 0; i < grades.length; i++) {
            Row r = s.createRow(2 + i);
            r.setHeightInPoints(18);
            String bg = (String) grades[i][3];
            XSSFCellStyle cs = cloneWithBg(wb, boldStyle,   bg);
            XSSFCellStyle ds = cloneWithBg(wb, normalStyle, bg);
            XSSFCellStyle ps = cloneWithBg(wb, pctStyle,    bg);
            cell(r, 0, (String) grades[i][0], cs);
            cell(r, 1, (String) grades[i][1], ds);
            long count = (long) grades[i][2];
            Cell cc = r.createCell(2); cc.setCellValue(count); cc.setCellStyle(ds);
            Cell pc = r.createCell(3); pc.setCellValue(n > 0 ? (double)count/n : 0.0); pc.setCellStyle(ps);
        }

        Row tot = s.createRow(7);
        tot.setHeightInPoints(18);
        cell(tot, 0, "TOTAL", boldStyle);
        cell(tot, 1, "",      boldStyle);
        Cell tc = tot.createCell(2); tc.setCellValue(n); tc.setCellStyle(boldStyle);
        Cell tp = tot.createCell(3); tp.setCellValue(1.0); tp.setCellStyle(pctStyle);
    }

    // ── Question Analysis ────────────────────────────────────────────────────

    void buildQuestions(XSSFWorkbook wb, List<String> qOrder,
                        Map<String, Double> maxScores,
                        Map<String, List<GradingResult>> byQuestion) {
        XSSFSheet s = wb.createSheet("Question Analysis");
        int[] widths = {4000,4500,4500,4500,4000,4000,4500,4500,4500,5500};
        for (int i = 0; i < widths.length; i++) s.setColumnWidth(i, widths[i]);

        sectionRow(s, 0, "QUESTION ANALYSIS", 10);
        Row hdr = s.createRow(1);
        hdr.setHeightInPoints(20);
        String[] hdrs = {"Question","Max Score","Class Avg","Avg %","Highest","Lowest","Pass","Fail","Pass Rate","Difficulty"};
        for (int i = 0; i < hdrs.length; i++) cell(hdr, i, hdrs[i], colHeaderStyle);

        int row = 2;
        for (int qi = 0; qi < qOrder.size(); qi++) {
            String qid = qOrder.get(qi);
            List<GradingResult> qr = byQuestion.getOrDefault(qid, Collections.emptyList());
            if (qr.isEmpty()) continue;

            double max     = maxScores.getOrDefault(qid, 0.0);
            double avg     = qr.stream().mapToDouble(GradingResult::getScore).average().orElse(0.0);
            double avgPct  = max > 0 ? avg / max * 100.0 : 0.0;
            double highest = qr.stream().mapToDouble(GradingResult::getScore).max().orElse(0.0);
            double lowest  = qr.stream().mapToDouble(GradingResult::getScore).min().orElse(0.0);
            long   passed  = qr.stream().filter(r -> r.getScore() > 0).count();
            long   failed  = qr.size() - passed;
            double passRate = (double) passed / qr.size();

            String bg = avgPct >= 85 ? COL_GREEN : avgPct >= 65 ? COL_LIGHT_BLU : avgPct >= 40 ? COL_YELLOW : COL_RED;
            XSSFCellStyle cs = cloneWithBg(wb, qi%2==1 ? altStyle : normalStyle, bg);
            XSSFCellStyle ps = cloneWithBg(wb, pctStyle, bg);

            Row r = s.createRow(row++);
            r.setHeightInPoints(18);
            cell(r, 0, qid, cs);
            numCell(r, 1, max,     cs);
            numCell(r, 2, avg,     cs);
            Cell ap = r.createCell(3); ap.setCellValue(avgPct/100.0); ap.setCellStyle(ps);
            numCell(r, 4, highest, cs);
            numCell(r, 5, lowest,  cs);
            cell(r, 6, passed + " of " + qr.size(), cs);
            cell(r, 7, failed + " of " + qr.size(), cs);
            Cell pp = r.createCell(8); pp.setCellValue(passRate); pp.setCellStyle(ps);
            cell(r, 9, difficulty(avgPct), cs);
        }
    }

    // ── Student Ranking ──────────────────────────────────────────────────────

    void buildRanking(XSSFWorkbook wb, List<StudentRecord> ranked, double totalMax) {
        XSSFSheet s = wb.createSheet("Student Ranking");
        int[] widths = {3000,7500,5000,5000,5000,3500,3500,4500};
        for (int i = 0; i < widths.length; i++) s.setColumnWidth(i, widths[i]);

        sectionRow(s, 0, "STUDENT RANKING", 8);
        Row hdr = s.createRow(1);
        hdr.setHeightInPoints(20);
        String[] hdrs = {"Rank","Username","Total Score","Max Possible","Percentage","Grade","Status","Percentile"};
        for (int i = 0; i < hdrs.length; i++) cell(hdr, i, hdrs[i], colHeaderStyle);

        for (int i = 0; i < ranked.size(); i++) {
            StudentRecord sr = ranked.get(i);
            int percentile   = ranked.size() > 1
                    ? (int) Math.round((double)(ranked.size()-1-i)/(ranked.size()-1)*100) : 100;
            String bg = sr.pct >= PASS ? COL_GREEN : COL_RED;
            XSSFCellStyle cs   = cloneWithBg(wb, normalStyle, bg);
            XSSFCellStyle ps   = cloneWithBg(wb, pctStyle,    bg);
            XSSFCellStyle bold = cloneWithBg(wb, boldStyle,   bg);

            Row r = s.createRow(2 + i);
            r.setHeightInPoints(18);
            numCell(r, 0, i+1,        cs);
            cell(r,   1, sr.username, cs);
            numCell(r, 2, sr.total,   cs);
            numCell(r, 3, totalMax,   cs);
            Cell pc = r.createCell(4); pc.setCellValue(sr.pct/100.0); pc.setCellStyle(ps);
            cell(r, 5, sr.grade,                       bold);
            cell(r, 6, sr.pct >= PASS ? "PASS" : "FAIL", bold);
            cell(r, 7, "Top " + percentile + "%",      cs);
        }
    }

    // ── Performance Matrix ───────────────────────────────────────────────────

    void buildMatrix(XSSFWorkbook wb, List<StudentRecord> ranked,
                     List<String> qOrder, Map<String, Double> maxScores,
                     double totalMax, double classAvg, double classAvgPct,
                     Map<String, List<GradingResult>> byQuestion) {
        XSSFSheet s = wb.createSheet("Performance Matrix");
        s.setColumnWidth(0, 7000);
        for (int i = 1; i <= qOrder.size()+2; i++) s.setColumnWidth(i, 3800);

        sectionRow(s, 0, "PERFORMANCE MATRIX  (student × question scores)", qOrder.size()+3);

        Row hdr = s.createRow(1);
        hdr.setHeightInPoints(20);
        cell(hdr, 0, "Username", colHeaderStyle);
        for (int i = 0; i < qOrder.size(); i++) cell(hdr, 1+i, qOrder.get(i), colHeaderStyle);
        cell(hdr, 1+qOrder.size(), "Total",      colHeaderStyle);
        cell(hdr, 2+qOrder.size(), "Percentage", colHeaderStyle);

        for (int i = 0; i < ranked.size(); i++) {
            StudentRecord sr = ranked.get(i);
            boolean alt = i % 2 == 1;
            Row r = s.createRow(2+i);
            r.setHeightInPoints(18);
            cell(r, 0, sr.username, alt ? altStyle : normalStyle);
            for (int qi = 0; qi < qOrder.size(); qi++) {
                double score = sr.qScores.getOrDefault(qOrder.get(qi), 0.0);
                double qMax  = maxScores.getOrDefault(qOrder.get(qi), 1.0);
                double sPct  = qMax > 0 ? score / qMax : 0.0;
                String bg    = sPct >= 1.0 ? COL_GREEN : sPct > 0 ? COL_YELLOW : COL_RED;
                numCell(r, 1+qi, score, cloneWithBg(wb, normalStyle, bg));
            }
            numCell(r, 1+qOrder.size(), sr.total, alt ? altStyle : normalStyle);
            Cell pc = r.createCell(2+qOrder.size());
            pc.setCellValue(sr.pct/100.0);
            pc.setCellStyle(alt ? pctAltStyle : pctStyle);
        }

        Row mr = s.createRow(2+ranked.size());
        mr.setHeightInPoints(18);
        cell(mr, 0, "MAX POSSIBLE", boldStyle);
        for (int qi = 0; qi < qOrder.size(); qi++)
            numCell(mr, 1+qi, maxScores.getOrDefault(qOrder.get(qi), 0.0), boldStyle);
        numCell(mr, 1+qOrder.size(), totalMax, boldStyle);
        Cell mp = mr.createCell(2+qOrder.size()); mp.setCellValue(1.0); mp.setCellStyle(pctStyle);

        Row ar = s.createRow(3+ranked.size());
        ar.setHeightInPoints(18);
        cell(ar, 0, "CLASS AVERAGE", boldStyle);
        for (int qi = 0; qi < qOrder.size(); qi++) {
            List<GradingResult> qr = byQuestion.getOrDefault(qOrder.get(qi), Collections.emptyList());
            numCell(ar, 1+qi, qr.stream().mapToDouble(GradingResult::getScore).average().orElse(0.0), boldStyle);
        }
        numCell(ar, 1+qOrder.size(), classAvg, boldStyle);
        Cell ap = ar.createCell(2+qOrder.size()); ap.setCellValue(classAvgPct/100.0); ap.setCellStyle(pctStyle);
    }

    // ── Style initialisation ─────────────────────────────────────────────────

    void initStyles(XSSFWorkbook wb) {
        Font nf = wb.createFont(); nf.setFontName("Arial"); nf.setFontHeightInPoints((short)10);
        Font bf = wb.createFont(); bf.setFontName("Arial"); bf.setFontHeightInPoints((short)10); bf.setBold(true);
        Font wf = wb.createFont(); wf.setFontName("Arial"); wf.setFontHeightInPoints((short)16); wf.setBold(true); wf.setColor(IndexedColors.WHITE.getIndex());
        Font sf = wb.createFont(); sf.setFontName("Arial"); sf.setFontHeightInPoints((short)12); sf.setBold(true); sf.setColor(IndexedColors.WHITE.getIndex());
        Font hf = wb.createFont(); hf.setFontName("Arial"); hf.setFontHeightInPoints((short)10); hf.setBold(true); hf.setColor(IndexedColors.WHITE.getIndex());
        DataFormat df = wb.createDataFormat();

        titleStyle = wb.createCellStyle();
        titleStyle.setFillForegroundColor(new XSSFColor(hexRGB(COL_NAVY), null));
        titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        titleStyle.setFont(wf);

        sectionStyle = wb.createCellStyle();
        sectionStyle.setFillForegroundColor(new XSSFColor(hexRGB(COL_BLUE), null));
        sectionStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        sectionStyle.setFont(sf);

        colHeaderStyle = wb.createCellStyle();
        colHeaderStyle.setFillForegroundColor(new XSSFColor(hexRGB(COL_NAVY), null));
        colHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        colHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        colHeaderStyle.setBorderBottom(BorderStyle.THIN);
        colHeaderStyle.setFont(hf);

        normalStyle = wb.createCellStyle(); normalStyle.setFont(nf);
        altStyle    = wb.createCellStyle(); altStyle.setFont(nf);
        altStyle.setFillForegroundColor(new XSSFColor(hexRGB(COL_LIGHT_BLU), null));
        altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        boldStyle   = wb.createCellStyle(); boldStyle.setFont(bf);

        pctStyle = wb.createCellStyle(); pctStyle.setFont(nf);
        pctStyle.setDataFormat(df.getFormat("0.0%"));
        pctStyle.setAlignment(HorizontalAlignment.CENTER);

        pctAltStyle = wb.createCellStyle(); pctAltStyle.setFont(nf);
        pctAltStyle.setDataFormat(df.getFormat("0.0%"));
        pctAltStyle.setAlignment(HorizontalAlignment.CENTER);
        pctAltStyle.setFillForegroundColor(new XSSFColor(hexRGB(COL_LIGHT_BLU), null));
        pctAltStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    // ── Cell helpers ─────────────────────────────────────────────────────────

    void sectionRow(XSSFSheet s, int rowIdx, String title, int cols) {
        Row r = s.createRow(rowIdx);
        r.setHeightInPoints(22);
        XSSFCell c = (XSSFCell) r.createCell(0);
        c.setCellValue(title);
        c.setCellStyle(sectionStyle);
        if (cols > 1) s.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, cols-1));
    }

    void cell(Row r, int col, String val, CellStyle style) {
        Cell c = r.createCell(col); c.setCellValue(val); c.setCellStyle(style);
    }

    void numCell(Row r, int col, double val, CellStyle style) {
        Cell c = r.createCell(col); c.setCellValue(val); c.setCellStyle(style);
    }

    XSSFCellStyle cloneWithBg(XSSFWorkbook wb, XSSFCellStyle base, String hex) {
        XSSFCellStyle s = wb.createCellStyle();
        s.cloneStyleFrom(base);
        s.setFillForegroundColor(new XSSFColor(hexRGB(hex), null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    static byte[] hexRGB(String hex) {
        return new byte[]{
            (byte) Integer.parseInt(hex.substring(0,2), 16),
            (byte) Integer.parseInt(hex.substring(2,4), 16),
            (byte) Integer.parseInt(hex.substring(4,6), 16)
        };
    }

    // ── Data model ────────────────────────────────────────────────────────────

    static class StudentRecord {
        String username; double total, pct; String grade; Map<String, Double> qScores;
        StudentRecord(String u, double t, double p, String g, Map<String, Double> q) {
            username=u; total=t; pct=p; grade=g; qScores=q;
        }
    }

    List<StudentRecord> buildRanked(Map<String, List<GradingResult>> map, double totalMax) {
        List<StudentRecord> list = new ArrayList<>();
        for (Map.Entry<String, List<GradingResult>> e : map.entrySet()) {
            Map<String, Double> qs = new LinkedHashMap<>();
            double total = 0.0;
            for (GradingResult r : e.getValue()) {
                qs.put(r.getTask().getQuestionId(), r.getScore());
                total += r.getScore();
            }
            double pct = totalMax > 0 ? total/totalMax*100.0 : 0.0;
            list.add(new StudentRecord(e.getKey(), total, pct, grade(pct), qs));
        }
        list.sort((a, b) -> Double.compare(b.total, a.total));
        return list;
    }

    private String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.2f", v);
    }

    private double calcMedian(List<Double> v) {
        if (v.isEmpty()) return 0.0;
        List<Double> s = new ArrayList<>(v);
        Collections.sort(s);
        int n = s.size();
        return n % 2 == 0 ? (s.get(n/2 - 1) + s.get(n/2)) / 2.0 : s.get(n/2);
    }

    private double calcStdDev(List<Double> v, double mean) {
        if (v.size() < 2) return 0.0;
        return Math.sqrt(v.stream().mapToDouble(x -> Math.pow(x - mean, 2)).sum() / v.size());
    }

    private String grade(double p) {
        if (p >= 80) return "A"; if (p >= 70) return "B";
        if (p >= 60) return "C"; if (p >= 50) return "D"; return "F";
    }

    private String difficulty(double p) {
        if (p >= 85) return "Easy"; if (p >= 65) return "Moderate";
        if (p >= 40) return "Hard"; return "Very Hard";
    }
}