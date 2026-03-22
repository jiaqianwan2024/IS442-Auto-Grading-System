@echo off
:: ============================================================================
:: run-cli.bat — IS442 Auto-Grading System (CLI mode)
::
:: WHAT THIS DOES (in order):
::   1. Loads COHERE_API_KEY from .env
::   2. Builds the project if the jar is missing
::   3. Runs the full pipeline:
::        a. Parse exam paper PDF for mark weights  (resources\input\exam\*.pdf)
::        b. Generate *Tester.java via Cohere LLM   (resources\input\testers\)
::        c. Grade all student submissions
::        d. Export score sheet
::
:: REQUIREMENTS:
::   - .env file:  COHERE_API_KEY=your-key  (free at dashboard.cohere.com)
::   - resources\input\submissions\student-submission.zip
::   - resources\input\template\RenameToYourUsername.zip
::   - resources\input\exam\exam-paper.pdf       (required - for mark weights)
::   - config\IS442-ScoreSheet.csv
::
:: OUTPUT:
::   resources\output\reports\IS442-ScoreSheet-Updated.xlsx
::   resources\output\reports\IS442-Plagiarism-Report.xlsx
:: ============================================================================

:: Load .env if present
if exist .env (
    echo Loading API key from .env...
    for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
        if not "%%A"=="" if not "%%A:~0,1%"=="#" set %%A=%%B
    )
) else (
    echo No .env file found. Checking environment for COHERE_API_KEY...
)

if "%COHERE_API_KEY%"=="" (
    echo.
    echo ERROR: COHERE_API_KEY is not set.
    echo    Create a .env file with: COHERE_API_KEY=your-key-here
    echo    Get a free key at: https://dashboard.cohere.com/api-keys
    echo.
    pause
    exit /b 1
)
echo API key found.
echo.

:: Find or build jar
set JAR=
for /f "delims=" %%f in ('dir /b target\*.jar 2^>nul ^| findstr /v sources ^| findstr /v javadoc') do set JAR=target\%%f

if "%JAR%"=="" (
    echo Building project...
    call mvn clean package -DskipTests -q
    echo Build complete.
    echo.
    for /f "delims=" %%f in ('dir /b target\*.jar 2^>nul ^| findstr /v sources ^| findstr /v javadoc') do set JAR=target\%%f
)

if "%JAR%"=="" (
    echo ERROR: Build failed - no jar found in target\.
    echo Run manually: mvn clean package -DskipTests
    pause
    exit /b 1
)

:: Run
echo ============================================
echo  IS442 AUTO-GRADING SYSTEM - CLI MODE
echo ============================================
echo.
echo Tester generation runs automatically (Phase 3).
echo No web server will start. Output shown below.
echo.

java --enable-native-access=ALL-UNNAMED -jar "%JAR%" --spring.profiles.active=cli

echo.
echo Done. Reports are in: resources\output\reports\
echo.
pause