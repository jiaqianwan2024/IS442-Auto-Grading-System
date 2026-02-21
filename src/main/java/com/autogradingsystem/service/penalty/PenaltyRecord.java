package com.autogradingsystem.penalty.model;

public class PenaltyRecord {
    private String studentId;
    private double penaltyValue;
    private String reason;
    private boolean isPercentage;

    public PenaltyRecord(String studentId, double penaltyValue, String reason, boolean isPercentage) {
        this.studentId = studentId;
        this.penaltyValue = penaltyValue;
        this.reason = reason;
        this.isPercentage = isPercentage;
    }

    // Getters
    public String getStudentId() { return studentId; }
    public double getPenaltyValue() { return penaltyValue; }
    public String getReason() { return reason; }
    public boolean isPercentage() { return isPercentage; }
}