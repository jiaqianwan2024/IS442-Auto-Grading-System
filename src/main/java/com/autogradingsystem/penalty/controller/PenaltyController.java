package com.autogradingsystem.penalty.controller;

import com.autogradingsystem.penalty.service.PenaltyCalculator;

public class PenaltyController {
    private PenaltyCalculator calculator;

    public PenaltyController() {
        this.calculator = new PenaltyCalculator();
        // Adjust this path if your CSV is elsewhere
        this.calculator.loadExternalPenalties("resources/input/penalties.csv");
    }

    // Step 1: Called for EACH question
    public double calculateQuestionScore(String qName, double raw, boolean struct, boolean head, boolean comp) {
        return calculator.calculateQuestionScore(qName, raw, struct, head, comp);
    }

    // Step 2: Called once at the end for the student
    public double computeFinalTotal(String id, double totalPoints) {
        return calculator.calculateFinalTotal(id, totalPoints);
    }

    public String getPenaltyReport() {
        return calculator.getFullReport();
    }
}