@echo off
:: ============================================================================
:: run-web.bat — IS442 Auto-Grading System (Web UI mode)
::
:: WHAT THIS DOES:
::   1. Loads COHERE_API_KEY from .env (if present)
::   2. Runs mvn clean install
::   3. Starts the Spring Boot web server on http://localhost:9090
::
:: USAGE:
::   run-web.bat
::   Open http://localhost:9090 in your browser
::   Press Ctrl+C to stop
:: ============================================================================

setlocal enabledelayedexpansion

if exist "resources\assessments" (
    echo Clearing resources\assessments...
    for /d %%D in ("resources\assessments\*") do rd /s /q "%%~fD"
    del /f /q "resources\assessments\*" >nul 2>&1
    echo Assessment folder cleared.
    echo.
)

:: ── Load .env if present ─────────────────────────────────────────────────────
if exist .env (
    echo Loading .env...
    for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do (
        set "%%A=%%B"
    )
) else (
    echo No .env file found. Checking environment for COHERE_API_KEY...
)

if "%COHERE_API_KEY%"=="" (
    echo WARNING: COHERE_API_KEY is not set.
    echo    Create a .env file with: COHERE_API_KEY=your-key-here
    echo    Get a free key at: https://dashboard.cohere.com/api-keys
    echo    Continuing without API key...
    echo.
) else (
    echo API key found.
    echo.
)

:: ── Find or build jar ─────────────────────────────────────────────────────────
echo Running Maven clean install...
call mvn clean install -q
if errorlevel 1 (
    echo.
    echo ERROR: Maven build failed.
    echo Run manually: mvn clean install
    pause
    exit /b 1
)
echo Build complete.
echo.

set "JAR="
for /f "delims=" %%f in ('dir /b target\*.jar 2^>nul ^| findstr /v sources ^| findstr /v javadoc') do set "JAR=target\%%f"

if "!JAR!"=="" (
    echo ERROR: No jar found in target\ after build.
    echo Run manually: mvn clean install
    pause
    exit /b 1
)

:: ── Start server ──────────────────────────────────────────────────────────────
echo ============================================
echo  IS442 AUTO-GRADING SYSTEM - WEB UI MODE
echo ============================================
echo.
echo JAR: !JAR!
echo Starting web server...
echo Open browser at: http://localhost:9090
echo Press Ctrl+C to stop.
echo.

java --enable-native-access=ALL-UNNAMED -jar "!JAR!" --spring.profiles.active=web

endlocal
