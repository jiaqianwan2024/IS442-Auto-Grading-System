package com.autogradingsystem.penalty.controller;

import com.autogradingsystem.penalty.service.PenaltyCalculator;

public class PenaltyController {
    private PenaltyCalculator calculator;

    public PenaltyController() {
        this.calculator = new PenaltyCalculator();
        this.calculator.loadExternalPenalties("resources/input/penalties.csv");
    }

    public double computeFinalScore(String id, double raw, boolean struct, boolean head, boolean comp) {
        return calculator.calculateTotalPenalty(id, raw, struct, head, comp);
    }

    public String getPenaltyReport() {
        return calculator.getDetailedReport();
    }
}