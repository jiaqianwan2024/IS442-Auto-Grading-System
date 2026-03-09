package com.autogradingsystem.controller;

import com.autogradingsystem.service.GradingService;
import com.autogradingsystem.service.GradingService.GradingReport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * GradingController - Handles all web UI requests
 * 
 * ROUTES:
 *   GET  /           → Home page (upload form)
 *   POST /upload     → Handle ZIP upload
 *   POST /grade      → Run grading pipeline
 *   GET  /download   → Download score sheet CSV
 * 
 * @author IS442 Team
 * @version 1.0
 */
@Controller
public class GradingController {

    @Autowired
    private GradingService gradingService;

    /**
     * Home page - shows the upload form
     */
    @GetMapping("/")
    public String home() {
        return "index";
    }

    /**
     * Handle file upload - saves submission ZIP to the input folder
     */
    @PostMapping("/upload")
    public String handleUpload(@RequestParam("submissionZip") MultipartFile submissionZip,
                               Model model) {
        try {
            if (submissionZip.isEmpty()) {
                model.addAttribute("error", "Please select a ZIP file to upload.");
                return "index";
            }

            // Save uploaded ZIP to resources/input/submissions/
            Path destination = Paths.get("resources/input/submissions/student-submission.zip");
            Files.createDirectories(destination.getParent());
            Files.copy(submissionZip.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            model.addAttribute("uploadSuccess", true);
            model.addAttribute("fileName", submissionZip.getOriginalFilename());
            model.addAttribute("fileSize", submissionZip.getSize() / 1024); // KB

        } catch (IOException e) {
            model.addAttribute("error", "Upload failed: " + e.getMessage());
        }

        return "index";
    }

    /**
     * Run the grading pipeline
     */
    @PostMapping("/grade")
    public String runGrading(Model model) {
        GradingReport report = gradingService.runFullPipeline();

        model.addAttribute("report", report);
        model.addAttribute("logs", report.getLogs());
        model.addAttribute("success", report.isSuccess());
        model.addAttribute("studentCount", report.getStudentCount());

        if (report.isSuccess() && report.getResults() != null) {
            model.addAttribute("results", report.getResults());
        }

        return "results";
    }

    /**
     * Download the updated score sheet CSV
     */
    @GetMapping("/download/csv")
    public ResponseEntity<Resource> downloadCsv() {
        File file = new File("resources/output/reports/IS442-ScoreSheet-Updated.csv");
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=IS442-ScoreSheet-Updated.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(resource);
    }

    /**
     * Download the statistics Excel report
     */
    @GetMapping("/download/excel")
    public ResponseEntity<Resource> downloadExcel() {
        File file = new File("resources/output/reports/IS442-Statistics.xlsx");
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=IS442-Statistics.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }
}