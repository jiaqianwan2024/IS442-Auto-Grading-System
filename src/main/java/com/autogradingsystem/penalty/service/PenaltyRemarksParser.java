package com.autogradingsystem.penalty.service;

import java.util.*;

public class PenaltyRemarksParser {

    public static final double RULE1_RATE = 0.05;
    public static final double RULE2_RATE = 0.05;
    public static final double RULE3_RATE = 0.20;
    public static final double RULE4_RATE = 0.20;

    public PenaltyResult parse(String gradeRemark,
                               Map<String, Double> qScores,
                               Map<String, Double> qMaxScores,
                               double totalDenominator,
                               double rawTotal) {

        Map<String, Double> adjQ = new LinkedHashMap<>(qScores);
        double adjusted = rawTotal;
        List<String> parts = new ArrayList<>();

        if (gradeRemark == null || gradeRemark.isBlank()) {
            return new PenaltyResult(rawTotal, adjusted, Collections.unmodifiableMap(adjQ), "No penalty");
        }

        // Rule 1: Incorrect Root Folder Name
        if (gradeRemark.contains("NoFolderRename")) {
            double deduction = totalDenominator * RULE1_RATE;
            adjusted = Math.max(0.0, adjusted - deduction);
            parts.add(String.format("Penalty 1: total is %.2f", adjusted));
        }

        // Rule 2: Incorrect Folder Hierarchy
        // Token: "NestedFolder:Q2" or "WrongFolder:Q1"
        for (String seg : gradeRemark.split(";")) {
            String s = seg.trim();
            String qId = null;
            if (s.startsWith("NestedFolder:")) {
                qId = s.substring("NestedFolder:".length()).trim();
            } else if (s.startsWith("WrongFolder:")) {
                qId = s.substring("WrongFolder:".length()).trim();
            }
            if (qId != null) {
                double maxScore  = qMaxScores.getOrDefault(qId, 0.0);
                double deduction = maxScore * RULE2_RATE;
                double oldQ      = adjQ.getOrDefault(qId, 0.0);
                double newQ      = Math.max(0.0, oldQ - deduction);
                adjQ.put(qId, newQ);
                adjusted = adjusted - oldQ + newQ;
                parts.add(String.format("Penalty 2: %s score is %.2f", qId, newQ));
            }
        }

        // Rule 3: Missing / Incorrect Header
        Set<String> headerQIds = new LinkedHashSet<>();
        for (String seg : gradeRemark.split(";")) {
            String s = seg.trim();
            if (s.startsWith("NoHeader:")) {
                String fn = s.substring("NoHeader:".length()).trim();
                if (fn.endsWith(".java")) headerQIds.add(fn.substring(0, fn.length() - 5));
            }
        }
        if (gradeRemark.contains("HeaderMismatch")) {
            String key = "found in ";
            int idx = gradeRemark.indexOf(key);
            while (idx >= 0) {
                int end = gradeRemark.indexOf(".java", idx + key.length());
                if (end > idx + key.length()) {
                    headerQIds.add(gradeRemark.substring(idx + key.length(), end).trim());
                }
                idx = gradeRemark.indexOf(key, idx + 1);
            }
        }
        for (String qId : headerQIds) {
            double oldQ = adjQ.getOrDefault(qId, 0.0);
            double newQ = Math.max(0.0, oldQ - oldQ * RULE3_RATE);
            adjusted    = adjusted - oldQ + newQ;
            adjQ.put(qId, newQ);
            parts.add(String.format("Penalty 3: %s score is %.2f", qId, newQ));
        }

        // Rule 4: Wrong / Extra Package
        for (String seg : gradeRemark.split(";")) {
            String s = seg.trim();
            if (s.contains(":WrongPackage:")) {
                String qId  = s.substring(0, s.indexOf(":WrongPackage:")).trim();
                double oldQ = adjQ.getOrDefault(qId, 0.0);
                double newQ = Math.max(0.0, oldQ - oldQ * RULE4_RATE);
                adjusted    = adjusted - oldQ + newQ;
                adjQ.put(qId, newQ);
                parts.add(String.format("Penalty 4: %s score is %.2f", qId, newQ));
            }
        }

        adjusted = Math.max(0.0, adjusted);
        String remarks = parts.isEmpty() ? "No penalty" : String.join("; ", parts);
        return new PenaltyResult(rawTotal, adjusted, Collections.unmodifiableMap(adjQ), remarks);
    }

    public static class PenaltyResult {
        public final double rawTotal;
        public final double adjustedTotal;
        public final Map<String, Double> adjustedQScores;
        public final String remarks;

        public PenaltyResult(double rawTotal, double adjustedTotal,
                             Map<String, Double> adjustedQScores, String remarks) {
            this.rawTotal        = rawTotal;
            this.adjustedTotal   = adjustedTotal;
            this.adjustedQScores = adjustedQScores;
            this.remarks         = remarks;
        }
    }
}
