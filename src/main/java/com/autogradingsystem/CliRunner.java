package com.autogradingsystem;

import com.autogradingsystem.service.GradingService;
import com.autogradingsystem.service.GradingService.GradingReport;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * CliRunner — Console-mode entry point for the grading pipeline.
 *
 * Activated only when the "cli" Spring profile is active (run-cli.bat / run-cli.sh).
 * The "web" profile (run-web.bat / run-web.sh) does NOT load this bean, so there
 * is zero conflict between the two modes.
 *
 * When active:
 *   - application-cli.properties sets spring.main.web-application-type=none
 *     → No Tomcat server starts, so port 8080 is never touched.
 *   - This bean runs runFullPipeline() directly and prints results to the console.
 *   - The web layer (GradingController) is not loaded in CLI mode.
 *
 * Usage:
 *   Windows : run-cli.bat
 *   Mac/Linux: ./run-cli.sh
 */
@Component
@Profile("cli")   // Only active in CLI profile — never loaded in web mode
public class CliRunner implements CommandLineRunner {

    private final GradingService gradingService;

    public CliRunner(GradingService gradingService) {
        this.gradingService = gradingService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=======================================================");
        System.out.println("  IS442 AUTO-GRADING SYSTEM — CLI MODE");
        System.out.println("=======================================================");
        System.out.println();

        GradingReport report = gradingService.runFullPipeline();

        System.out.println();
        System.out.println("-------------------------------------------------------");
        System.out.println("  GRADING LOG");
        System.out.println("-------------------------------------------------------");
        for (String log : report.getLogs()) {
            System.out.println(log);
        }

        System.out.println();
        System.out.println("-------------------------------------------------------");
        if (report.isSuccess()) {
            System.out.println("  ✅ GRADING COMPLETE — " + report.getStudentCount() + " student(s) processed.");
            System.out.println("  Reports saved to: resources/output/reports/");
        } else {
            System.out.println("  ❌ GRADING FAILED — check log above for details.");
        }
        System.out.println("=======================================================");
    }
}