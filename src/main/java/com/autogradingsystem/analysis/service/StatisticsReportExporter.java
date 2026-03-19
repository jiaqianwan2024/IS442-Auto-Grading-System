package com.autogradingsystem.analysis.service;

import com.autogradingsystem.model.GradingResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * @deprecated v3.4 — Statistics sheets are now embedded directly in
 * IS442-ScoreSheet-Updated.xlsx by {@link ScoreSheetExporter}.
 * This class is retained only for backward compatibility with any
 * external callers; it performs NO file I/O and returns null.
 *
 * <p>The former IS442-Statistics.xlsx is no longer generated.</p>
 * <p>The former "Anomaly Report" sheet has been removed per v3.4 requirements.</p>
 */
@Deprecated
public class StatisticsReportExporter {

    /**
     * No-op. Statistics are now exported by {@link ScoreSheetExporter}.
     *
     * @return null — no file is written
     */
    public Path export(Map<String, List<GradingResult>> byStudent) {
        System.out.println("ℹ️  StatisticsReportExporter is deprecated (v3.4): " +
                "statistics sheets are now embedded in IS442-ScoreSheet-Updated.xlsx. " +
                "No separate IS442-Statistics.xlsx will be created.");
        return null;
    }
}