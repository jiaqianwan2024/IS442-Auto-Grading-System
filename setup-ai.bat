@echo off
echo ============================================
echo  IS442 AI Setup - Ollama + qwen2.5-coder:7b
echo ============================================
echo.

REM Check if Ollama is already installed
where ollama >nul 2>&1
if %errorlevel% == 0 (
    echo [OK] Ollama is already installed.
    goto pull_model
)

echo [1/2] Downloading Ollama installer...
powershell -Command "Invoke-WebRequest -Uri 'https://ollama.com/download/OllamaSetup.exe' -OutFile '$env:TEMP\OllamaSetup.exe'"
if %errorlevel% neq 0 (
    echo [ERROR] Download failed. Check your internet connection and try again.
    pause
    exit /b 1
)

echo.
echo [1/2] Running Ollama installer...
echo       Click through the installer, then return to this window.
start /wait "%TEMP%\OllamaSetup.exe"
del "%TEMP%\OllamaSetup.exe"

REM Refresh PATH so ollama is found in this session without reopening terminal
set "PATH=%PATH%;%LOCALAPPDATA%\Programs\Ollama"

:pull_model
echo.
echo [2/2] Downloading qwen2.5-coder:7b model (~4.7GB)...
echo       This only happens once. Please wait.
ollama pull qwen2.5-coder:7b
if %errorlevel% neq 0 (
    echo [ERROR] Model download failed.
    echo         Make sure Ollama installed correctly, then run this script again.
    pause
    exit /b 1
)

echo.
echo ============================================
echo  Setup complete. You can now run the grader.
echo ============================================
pause
