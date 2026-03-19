@echo off
:: ============================================================================
:: run-web.bat — IS442 Auto-Grading System (Web UI mode)
::
:: WHAT THIS DOES:
::   1. Loads COHERE_API_KEY from .env
::   2. Builds the project if the jar is missing
::   3. Starts the Spring Boot web server on http://localhost:8080
::
:: REQUIREMENTS:
::   - .env file: COHERE_API_KEY=your-key  (free at dashboard.cohere.com)
::
:: USAGE:
::   run-web.bat
::   Open http://localhost:8080 in your browser
::   Press Ctrl+C to stop
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
echo  IS442 AUTO-GRADING SYSTEM - WEB UI MODE
echo ============================================
echo.
echo Starting web server...
echo Open browser at: http://localhost:8080
echo Press Ctrl+C to stop.
echo.

java --enable-native-access=ALL-UNNAMED -jar "%JAR%" --spring.profiles.active=web