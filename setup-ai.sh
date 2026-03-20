#!/bin/bash
echo "============================================"
echo " IS442 AI Setup - Ollama + qwen2.5-coder:7b"
echo "============================================"
echo ""

# ── Step 1: Install Ollama if not already present ─────────────────────────────

if command -v ollama &> /dev/null; then
    echo "[OK] Ollama is already installed."
else
    echo "[1/2] Installing Ollama..."

    if ! command -v curl &> /dev/null; then
        echo "[ERROR] curl is not installed."
        echo "        On Ubuntu/Debian: sudo apt install curl"
        echo "        On Fedora:        sudo dnf install curl"
        echo "        On Mac:           brew install curl"
        exit 1
    fi

    curl -fsSL https://ollama.com/install.sh | sh

    if [ $? -ne 0 ]; then
        echo "[ERROR] Ollama installation failed."
        echo "        Try installing manually from https://ollama.com/download"
        exit 1
    fi

    echo "[OK] Ollama installed successfully."
fi

# ── Step 2: Pull the model ─────────────────────────────────────────────────────

echo ""
echo "[2/2] Checking for qwen2.5-coder:7b model..."

if ollama list 2>/dev/null | grep -q "qwen2.5-coder:7b"; then
    echo "[OK] Model already downloaded."
else
    echo "[2/2] Downloading qwen2.5-coder:7b (~4.7GB, one time only)..."
    echo "      Please wait — this may take several minutes."
    ollama pull qwen2.5-coder:7b

    if [ $? -ne 0 ]; then
        echo "[ERROR] Model download failed."
        echo "        Make sure Ollama is running, then try again."
        exit 1
    fi

    echo "[OK] Model downloaded successfully."
fi

# ── Done ───────────────────────────────────────────────────────────────────────

echo ""
echo "============================================"
echo " Setup complete. You can now run the grader."
echo "============================================"
