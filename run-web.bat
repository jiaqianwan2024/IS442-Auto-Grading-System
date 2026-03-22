@echo off
setlocal enabledelayedexpansion

:: -- Check Ollama is running (via PowerShell) --------------------------------
echo Checking Ollama...
powershell -NoProfile -Command "try { Invoke-RestMethod http://localhost:11434/api/tags | Out-Null; exit 0 } catch { exit 1 }" >nul 2>&1
if errorlevel 1 (
    echo Ollama not responding. Attempting to start...
    powershell -NoProfile -Command "Start-Process ollama -ArgumentList 'serve' -WindowStyle Hidden"
    powershell -NoProfile -Command "Start-Sleep -Seconds 5"
    powershell -NoProfile -Command "try { Invoke-RestMethod http://localhost:11434/api/tags | Out-Null; exit 0 } catch { exit 1 }" >nul 2>&1
    if errorlevel 1 (
        echo.
        echo ERROR: Ollama is not responding on http://localhost:11434
        echo    Install from: https://ollama.com/download
        echo    Then run:     ollama pull qwen2.5-coder:7b
        echo.
        pause
        exit /b 1
    )
)
echo Ollama is running.
echo.

:: -- Find or build jar -------------------------------------------------------
set "JAR="
for /f "delims=" %%f in ('dir /b target\*.jar 2^>nul ^| findstr /v sources ^| findstr /v javadoc') do set "JAR=target\%%f"

if "!JAR!"=="" (
    echo Building project (this may take a minute)...
    call mvn clean package -DskipTests -q
    if errorlevel 1 (
        echo ERROR: Maven build failed. Run: mvn clean package -DskipTests
        pause
        exit /b 1
    )
    echo Build complete.
    echo.
    for /f "delims=" %%f in ('dir /b target\*.jar 2^>nul ^| findstr /v sources ^| findstr /v javadoc') do set "JAR=target\%%f"
)

if "!JAR!"=="" (
    echo ERROR: No jar found in target\. Run: mvn clean package -DskipTests
    pause
    exit /b 1
)

:: -- Start server ------------------------------------------------------------
echo ============================================
echo  IS442 AUTO-GRADING SYSTEM - WEB UI MODE
echo ============================================
echo.
echo JAR: !JAR!
echo Starting web server...
echo Open browser at: http://localhost:8080
echo Press Ctrl+C to stop.
echo.

java --enable-native-access=ALL-UNNAMED -jar "!JAR!" --spring.profiles.active=web

endlocal
