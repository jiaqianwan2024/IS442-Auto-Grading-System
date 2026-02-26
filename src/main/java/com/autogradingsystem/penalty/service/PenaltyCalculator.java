package com.autogradingsystem.penalty.service;

import com.autogradingsystem.penalty.model.PenaltyRecord;
import java.io.*;
import java.util.*;

public class PenaltyCalculator {
    // Stores manual penalties from CSV (e.g., Lateness)
    private Map<String, List<PenaltyRecord>> externalPenalties = new HashMap<>();
    private StringBuilder reportLog = new StringBuilder();

    /**
     * Loads the penalties.csv file. 
     * Handles the '#' prefix found in your IS442-ScoreSheet.csv
     */
    public void loadExternalPenalties(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = line.split(",");
                if (data.length >= 2) {
                    // Clean ID: removes '#' and whitespace to match the scoresheet format
                    String id = data[0].trim().replace("#", "").toLowerCase();
                    double value = Double.parseDouble(data[1].trim());
                    String reason = (data.length > 2) ? data[2].trim() : "Manual Deduction";
                    
                    externalPenalties.computeIfAbsent(id, k -> new ArrayList<>())
                                     .add(new PenaltyRecord(id, value, reason, false));
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.out.println("Warning: Could not load penalties.csv or format is incorrect.");
        }
    }

    /**
     * Calculates score for ONE specific question.
     * Applies 20% for structural/header errors and 50% for compile errors.
     */
    public double calculateQuestionScore(String qName, double rawScore, boolean structErr, boolean headerErr, boolean compileErr) {
        double currentQScore = rawScore;
        reportLog.append("--- ").append(qName).append(" Breakdown ---\n");

        // 1. Structural/Header Penalties (20% each)
        if (structErr) {
            double deduction = currentQScore * 0.20;
            currentQScore -= deduction;
            reportLog.append("  - Structural Error: -20% (-").append(deduction).append(" pts)\n");
        }
        if (headerErr) {
            double deduction = currentQScore * 0.20;
            currentQScore -= deduction;
            reportLog.append("  - Header Error: -20% (-").append(deduction).append(" pts)\n");
        }

        // 2. Per-Question Compilation Penalty (50%)
        if (compileErr) {
            double deduction = currentQScore * 0.50;
            currentQScore -= deduction;
            reportLog.append("  - Compilation Failure: -50% (-").append(deduction).append(" pts)\n");
        }

        reportLog.append("  Subtotal for ").append(qName).append(": ").append(currentQScore).append("\n\n");
        return currentQScore;
    }

    /**
     * Final step: Adds up all question scores and subtracts CSV penalties (Lateness).
     */
    public double calculateFinalTotal(String studentId, double totalFromAllQuestions) {
        double finalScore = totalFromAllQuestions;
        String cleanId = studentId.replace("#", "").toLowerCase();

        reportLog.append("--- Global Adjustments ---\n");
        
        if (externalPenalties.containsKey(cleanId)) {
            for (PenaltyRecord p : externalPenalties.get(cleanId)) {
                finalScore += p.getPenaltyValue(); // e.g., + (-5.0)
                reportLog.append("  - ").append(p.getReason()).append(": ").append(p.getPenaltyValue()).append(" pts\n");
            }
        } else {
            reportLog.append("  - No external penalties found.\n");
        }

        finalScore = Math.max(0, finalScore); // Score cannot be negative
        reportLog.append("FINAL GRADE: ").append(finalScore).append("\n");
        
        return finalScore;
    }

    public String getFullReport() {
        String report = reportLog.toString();
        reportLog.setLength(0); // Clear for next student
        return report;
    }
}