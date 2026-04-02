import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class Q4Tester {

    public static void main(String[] args) {
        double score = 0.0;

        System.out.println("-------------------------------------------------------");
        System.out.println("---------------------- Q4 -----------------------------");
        System.out.println("-------------------------------------------------------");

        try {
            // ProcessRunner sets the working directory to the student's Q4 folder
            File workingDir = new File(".");

            // ── SCRIPT DETECTION ──────────────────────────────────────────────
            // Rule from the exam question:
            //   Windows students must submit compile.bat / run.bat
            //   Mac students must submit compile.sh / run.sh
            //
            // We select which script to run based on WHAT THE STUDENT SUBMITTED,
            // not the OS the grading machine happens to be running on.
            // This ensures Mac students are graded correctly regardless of
            // whether the prof grades on Windows, Mac, or Linux.

            File compileBat = new File(workingDir, "compile.bat");
            File compileSh  = new File(workingDir, "compile.sh");

            // Determine student's platform from which script they submitted.
            // If a student submitted both (cross-platform), .bat takes precedence
            // only on a Windows grading machine; otherwise .sh is used.
            // If neither exists, fail immediately.
            boolean hasBat = compileBat.exists() && compileBat.length() > 0;
            boolean hasSh  = compileSh.exists()  && compileSh.length()  > 0;

            if (!hasBat && !hasSh) {
                System.out.println("Failed -> No compile.bat or compile.sh found!");
                System.out.println(score);
                return;
            }

            // Pick the compile script:
            //   - Student submitted .bat (Windows student) → always use cmd /c compile.bat
            //   - Student submitted only .sh (Mac student)  → always use sh compile.sh
            //   - Student submitted both → use .bat on Windows grading machine, .sh elsewhere
            boolean gradingOnWindows = System.getProperty("os.name")
                    .toLowerCase().contains("win");

            boolean usesBat = hasBat && (!hasSh || gradingOnWindows);

            // 1. Execute the student's compile script
            System.out.println("Executing compile script...");
            ProcessBuilder compilePb = new ProcessBuilder();

            if (usesBat) {
                compilePb.command("cmd", "/c", "compile.bat");
            } else {
                // Mac students submit .sh — use bash on Windows (Git Bash), sh on Mac/Linux
                if (gradingOnWindows) compilePb.command("bash", "compile.sh");
                else                  compilePb.command("sh",   "compile.sh");
            }

            compilePb.directory(workingDir);
            compilePb.redirectErrorStream(true);
            Process compileProcess = compilePb.start();

            BufferedReader compileReader = new BufferedReader(
                    new InputStreamReader(compileProcess.getInputStream()));
            StringBuilder compileOutput = new StringBuilder();
            String cl;
            while ((cl = compileReader.readLine()) != null) {
                compileOutput.append(cl).append("\n");
            }

            boolean compiled = compileProcess.waitFor(15, TimeUnit.SECONDS);

            if (!compiled || compileProcess.exitValue() != 0) {
                System.out.println("Compile script failed or timed out.");
                System.out.println(compileOutput);
                System.out.println(score);
                return;
            }

            // 2. Execute the student's run script
            System.out.println("Executing run script...");
            ProcessBuilder runPb = new ProcessBuilder();
            File runBat = new File(workingDir, "run.bat");
            File runSh  = new File(workingDir, "run.sh");

            boolean hasRunBat = runBat.exists() && runBat.length() > 0;
            boolean hasRunSh  = runSh.exists()  && runSh.length()  > 0;

            // Use the same platform decision as compile
            if (usesBat && hasRunBat) {
                runPb.command("cmd", "/c", "run.bat");
            } else if (hasRunSh) {
                if (gradingOnWindows) runPb.command("bash", "run.sh");
                else                  runPb.command("sh",   "run.sh");
            } else if (usesBat) {
                // Student submitted .bat for compile but no run.bat
                System.out.println("Failed -> Compile succeeded, but no run.bat found!");
                System.out.println(score);
                return;
            } else {
                // Student submitted .sh for compile but no run.sh
                System.out.println("Failed -> Compile succeeded, but no run.sh found!");
                System.out.println(score);
                return;
            }

            runPb.directory(workingDir);
            Process runProcess = runPb.start();

            // 3. Read and normalise output
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(runProcess.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line.trim()).append("\n");
            }
            runProcess.waitFor(10, TimeUnit.SECONDS);

            // 4. Compare against expected output
            String actual = output.toString().trim();
            String expected =
                "2\n" +
                "Person [name=Peter]\n" +
                "4\n" +
                "Person [name=Candy]\n" +
                "1\n" +
                "Person [name=John]\n" +
                "3\n" +
                "Person [name=Joe]\n" +
                "Number of Persons :\n" +
                "4";

            System.out.println("\n--- EXPECTED ---");
            System.out.println(expected);
            System.out.println("\n--- ACTUAL ---");
            System.out.println(actual);

            if (actual.equals(expected)) {
                score += 6.0;
                System.out.println("\nPassed");
            } else {
                // Fallback: alternate output format
                String expectedAlt =
                    "2 - Person[name=Peter]\n" +
                    "4 - Person[name=Candy]\n" +
                    "1 - Person[name=John]\n" +
                    "3 - Person[name=Joe]\n" +
                    "Number of Persons : 4";

                if (actual.equals(expectedAlt)) {
                    score = score + 6.0;
                    System.out.println("\nPassed (Matched alternative format)");
                } else {
                    System.out.println("\nFailed");
                }
            }

        } catch (Exception e) {
            System.out.println("Failed -> Exception occurred while running scripts.");
            e.printStackTrace();
        }

        System.out.println("-------------------------------------------------------");
        System.out.println(score);
    }
}