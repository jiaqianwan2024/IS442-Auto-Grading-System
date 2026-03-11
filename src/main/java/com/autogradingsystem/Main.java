package com.autogradingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


/**
 * Main - Spring Boot Entry Point
 *
 * MODES:
 *   Web UI  → run-web.bat / ./run-web.sh   → http://localhost:8080
 *   CLI     → run-cli.bat / ./run-cli.sh   → console output, no web server
 *
 * The old orchestration logic (ExtractionController → DiscoveryController →
 * ExecutionController → AnalysisController) now lives in GradingService.runFullPipeline().
 *
 * Web mode: GradingController (HTTP) calls GradingService
 * CLI mode: CliRunner (@Profile("cli")) calls GradingService — Tomcat never starts
 *
 * @version 3.0
 */
@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}