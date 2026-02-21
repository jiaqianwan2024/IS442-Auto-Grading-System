package com.autogradingsystem.penalty;

import com.autogradingsystem.penalty.controller.PenaltyController;

public class PenaltyTest {
    public static void main(String[] args) {
        // 1. Setup the "Front Desk"
        PenaltyController pc = new PenaltyController();

        System.out.println("=== TESTING PENALTY LOGIC ===");

        // SCENARIO A: The "Perfect Student"
        // Raw Score: 20.0, No Errors, Compiles
        double scoreA = pc.computeFinalScore("student.1", 20.0, false, false, false);
        System.out.println("Scenario A: Expected 20.0 | Result: " + scoreA);

        // SCENARIO B: The "Messy Folder" (-20%)
        // Raw Score: 20.0, hasStructuralError = true
        double scoreB = pc.computeFinalScore("student.2", 20.0, true, false, false);
        System.out.println("Scenario B: Expected 16.0 | Result: " + scoreB);

        // SCENARIO C: The "Non-Compiler" (-50%)
        // Raw Score: 20.0, didCompile = false
        double scoreC = pc.computeFinalScore("student.3", 20.0, false, false, true);
        System.out.println("Scenario C: Expected 10.0 | Result: " + scoreC);

        // SCENARIO D: The "Late Student" (Deduction from CSV)
        // Make sure your penalties.csv has: ping.lee.2023, -5.0, Late, false
        double scoreD = pc.computeFinalScore("ping.lee.2023", 20.0, false, false, false);
        System.out.println("Scenario D: Expected 15.0 | Result: " + scoreD);

        // BONUS FEATURE: See the explanation
        System.out.println("\n--- Detailed Report for Scenario B ---");
        pc.computeFinalScore("student.2", 20.0, true, false, false); // Run it again to set report
        System.out.println(pc.getPenaltyReport());
    }
}