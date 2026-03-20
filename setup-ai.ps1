Write-Host "============================================"
Write-Host " IS442 AI Setup - Ollama + qwen2.5-coder:7b"
Write-Host "============================================"
Write-Host ""

# ── Step 1: Install Ollama if not already present ─────────────────────────────

if (Get-Command ollama -ErrorAction SilentlyContinue) {
    Write-Host "[OK] Ollama is already installed." -ForegroundColor Green
} else {
    Write-Host "[1/2] Installing Ollama..." -ForegroundColor Cyan
    try {
        irm https://ollama.com/install.ps1 | iex
    } catch {
        Write-Host "[ERROR] Installation failed: $_" -ForegroundColor Red
        Write-Host "        Check your internet connection and try again."
        Read-Host "Press Enter to exit"
        exit 1
    }

    # Refresh PATH in this session so ollama is found immediately
    $env:PATH += ";$env:LOCALAPPDATA\Programs\Ollama"

    if (-not (Get-Command ollama -ErrorAction SilentlyContinue)) {
        Write-Host "[ERROR] Ollama not found after install." -ForegroundColor Red
        Write-Host "        Close and reopen VS Code, then run this script again."
        Read-Host "Press Enter to exit"
        exit 1
    }

    Write-Host "[OK] Ollama installed successfully." -ForegroundColor Green
}

# ── Step 2: Pull the model ─────────────────────────────────────────────────────

Write-Host ""
Write-Host "[2/2] Checking for qwen2.5-coder:7b model..." -ForegroundColor Cyan

$models = ollama list 2>$null
if ($models -match "qwen2.5-coder:7b") {
    Write-Host "[OK] Model already downloaded." -ForegroundColor Green
} else {
    Write-Host "[2/2] Downloading qwen2.5-coder:7b (~4.7GB, one time only)..." -ForegroundColor Cyan
    Write-Host "      Please wait — this may take several minutes."
    ollama pull qwen2.5-coder:7b

    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Model download failed." -ForegroundColor Red
        Write-Host "        Make sure Ollama is running, then try again."
        Read-Host "Press Enter to exit"
        exit 1
    }

    Write-Host "[OK] Model downloaded successfully." -ForegroundColor Green
}

# ── Done ───────────────────────────────────────────────────────────────────────

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host " Setup complete. You can now run the grader." -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Read-Host "Press Enter to exit"
