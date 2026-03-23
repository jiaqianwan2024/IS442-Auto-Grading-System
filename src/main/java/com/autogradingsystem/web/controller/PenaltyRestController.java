package com.autogradingsystem.web.controller;

import com.autogradingsystem.penalty.controller.PenaltyController;
import com.autogradingsystem.penalty.model.PenaltyGradingResult;
import com.autogradingsystem.penalty.model.ProcessedScore;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PenaltyRestController - REST API endpoints for the penalty microservice
 *
 * Provides HTTP endpoints for penalty calculation operations:
 * - Calculate penalties for a single grading result
 * - Calculate penalties for multiple results from one student
 * - Health check endpoint
 */
@RestController
@RequestMapping("/api/penalty")
public class PenaltyRestController {

    private final PenaltyController penaltyController;

    public PenaltyRestController() {
        this.penaltyController = new PenaltyController();
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "penalty-microservice",
            "strategies", String.valueOf(penaltyController.getConfiguredStrategyCount())
        ));
    }

    /**
     * Calculate penalties for a single grading result
     */
    @PostMapping("/single")
    public ResponseEntity<ProcessedScore> calculateSinglePenalty(@RequestBody PenaltyGradingResult result) {
        try {
            ProcessedScore processedScore = penaltyController.processSingleResult(result);
            return ResponseEntity.ok(processedScore);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Calculate penalties for multiple results from one student
     */
    @PostMapping("/student/{studentId}")
    public ResponseEntity<ProcessedScore> calculateStudentPenalties(
            @PathVariable String studentId,
            @RequestBody List<PenaltyGradingResult> results) {
        try {
            ProcessedScore processedScore = penaltyController.processStudentResults(studentId, results);
            return ResponseEntity.ok(processedScore);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Calculate penalties for multiple results from one student with custom penalties CSV
     */
    @PostMapping("/student/{studentId}/custom")
    public ResponseEntity<ProcessedScore> calculateStudentPenaltiesWithCustomCsv(
            @PathVariable String studentId,
            @RequestBody List<PenaltyGradingResult> results,
            @RequestParam String penaltiesCsvPath) {
        try {
            ProcessedScore processedScore = penaltyController.processStudentResults(studentId, results, penaltiesCsvPath);
            return ResponseEntity.ok(processedScore);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}