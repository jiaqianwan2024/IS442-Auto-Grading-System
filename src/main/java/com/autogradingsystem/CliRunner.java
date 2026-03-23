package com.autogradingsystem;

import com.autogradingsystem.web.service.GradingService;
import com.autogradingsystem.web.service.GradingService.GradingReport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * CliRunner - Spring Boot CLI entry point for the full grading pipeline.
 *
 * PACKAGE: com.autogradingsystem
 * ACTIVATED BY: --spring.profiles.active=cli  (via run-cli.sh / run-cli.bat)
 *
 * PIPELINE (handled inside GradingService.runFullPipeline()):
 *   1. Validate inputs
 *   2. Extract student submissions
 *   3. Generate *Tester.java files via LLM Oracle  ← automatic, no upload needed
 *   4. Discover grading plan
 *   5. Grade all students
 *   6. Plagiarism detection
 *   7. Export score sheet
 *
 * EXIT CODES:
 *   0 — pipeline completed successfully
 *   1 — pipeline failed (check logs above)
 */
@Component
@Profile("cli")
public class CliRunner implements CommandLineRunner {

    @Autowired
    private GradingService gradingService;

    @Override
    public void run(String... args) throws Exception {
        GradingReport report = gradingService.runFullPipeline();

        System.out.println();
        System.out.println("=================================================");
        if (report.isSuccess()) {
            System.out.println("  ✅ Grading complete.");
            System.out.println("  Students graded : " + report.getStudentCount());
            System.out.println("  Reports at      : resources/output/reports/");
        } else {
            System.out.println("  ❌ Grading failed. See logs above.");
        }
        System.out.println("=================================================");

        // Print all pipeline logs
        System.out.println();
        report.getLogs().forEach(System.out::println);

        System.exit(report.isSuccess() ? 0 : 1);
    }
}