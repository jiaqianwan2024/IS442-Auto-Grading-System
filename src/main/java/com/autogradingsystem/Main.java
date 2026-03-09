package com.autogradingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main - Spring Boot Entry Point
 * 
 * PURPOSE:
 * - Starts the Spring Boot web application
 * - Auto-scans all @Controller, @Service, @Component classes
 * - Opens the web UI at http://localhost:8080
 * 
 * @author IS442 Team
 * @version 3.0 (Spring Boot Web UI)
 */
@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
        System.out.println("\n╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                     IS442 AUTO-GRADING SYSTEM                      ║");
        System.out.println("║              Web UI running at http://localhost:8080                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝\n");
    }
}