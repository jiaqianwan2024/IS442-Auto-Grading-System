package com.autogradingsystem.penalty.service;

import com.autogradingsystem.penalty.model.PenaltyRecord;
import java.io.*;
import java.util.*;

public class PenaltyCalculator {
    private Map<String, List<PenaltyRecord>> externalPenalties = new HashMap<>();
    private String lastReport = ""; // Stores the detailed breakdown

    public void loadExternalPenalties(String filePath) {
        // (Keep the same CSV loading logic as before)
    }

    public double calculateTotalPenalty(String studentId, double rawScore, 
                                        boolean structErr, boolean headerErr, 
                                        boolean compileErr) {
        StringBuilder report = new StringBuilder();
        double currentScore = rawScore;
        report.append("Deduction Report for ").append(studentId).append(":\n");

        // 1. Structural Check
        if (structErr) {
            double loss = currentScore * 0.20;
            currentScore -= loss;
            report.append("- [STRUCTURAL]: -20% (").append(loss).append(" pts) for wrong folder/naming.\n");
        }

        // 2. Header Check
        if (headerErr) {
            double loss = currentScore * 0.20;
            currentScore -= loss;
            report.append("- [HEADER]: -20% (").append(loss).append(" pts) for missing name/email comments.\n");
        }

        // 3. Compilation Check
        if (compileErr) {
            double loss = currentScore * 0.50;
            currentScore -= loss;
            report.append("- [FATAL]: -50% (").append(loss).append(" pts) for Compilation Failure.\n");
        }

        // 4. CSV Check
        if (externalPenalties.containsKey(studentId)) {
            for (PenaltyRecord p : externalPenalties.get(studentId)) {
                currentScore += p.getPenaltyValue(); // Assuming value is negative like -5.0
                report.append("- [EXTERNAL]: ").append(p.getPenaltyValue()).append(" pts for ").append(p.getReason()).append(".\n");
            }
        }

        if (report.length() < 30) report.append("- No penalties applied.");
        this.lastReport = report.toString();
        
        return Math.max(0, currentScore);
    }

    public String getDetailedReport() {
        return lastReport;
    }
}