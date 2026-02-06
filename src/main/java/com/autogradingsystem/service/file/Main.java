package com.autogradingsystem.service.file; // Ensure this package matches your folder structure

// Import the Controller so we can use it
import com.autogradingsystem.controller.ExecutionController;

public class Main {
    
    // The standard Java entry point. 
    // "public static void main" is required for the JVM to know where to start.
    public static void main(String[] args) {
        System.out.println("--- Starting Grading Engine (Team B Mock Test) ---");

        // -----------------------------------------------------
        // 1. INSTANTIATE THE CONTROLLER
        // -----------------------------------------------------
        // We create an instance of ExecutionController.
        // This sets up the internal tools (Injector, Compiler, Runner) inside the controller.
        ExecutionController controller = new ExecutionController();

        // -----------------------------------------------------
        // 2. RUN THE GRADING LOGIC
        // -----------------------------------------------------
        // This single line triggers the entire complex process:
        //   1. Load students (chee.teo, david, etc.)
        //   2. Loop through Q1A, Q1B, Q2A...
        //   3. Inject Testers -> Compile -> Run -> Parse
        //   4. Print the final scoreboard
        controller.runGrading();

        System.out.println("--- Grading Process Finished ---");
    }
}