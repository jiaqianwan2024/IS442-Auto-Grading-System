package com.autogradingsystem.penalty.service;

import com.autogradingsystem.penalty.controller.PenaltyController;

public class PenaltyTest {
    public static void main(String[] args) {
        PenaltyController pc = new PenaltyController();
        double q1 = pc.calculateQuestionScore("Q1", 10.0, true, false, false);
        double total = pc.computeFinalTotal("student123", q1);
        System.out.println("Test Score: " + total);
    }
}