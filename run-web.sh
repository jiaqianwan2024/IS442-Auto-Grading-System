#!/bin/bash
# ============================================================================
# run-web.sh — IS442 Auto-Grading System (Web UI mode)
#
# WHAT THIS DOES:
#   1. Checks that Ollama is running (required for AI test case generation)
#   2. Builds the project if the jar is missing
#   3. Starts the Spring Boot web server on http://localhost:8080
#
# REQUIREMENTS:
#   - Ollama installed and running: https://ollama.com/download
#   - Model pulled: ollama pull qwen2.5-coder:7b
#
# USAGE:
#   chmod +x run-web.sh   (first time only)
#   ./run-web.sh
#   → Open http://localhost:8080 in your browser
#   → Press Ctrl+C to stop
# ============================================================================

set -e

# ── Check Ollama ──────────────────────────────────────────────────────────────
echo "🔍 Checking Ollama..."

if ! command -v ollama &>/dev/null; then
    echo ""
    echo "❌ Ollama is not installed."
    echo "   Download it at: https://ollama.com/download"
    echo "   Then run: ollama pull qwen2.5-coder:7b"
    echo ""
    exit 1
fi

if ! curl -sf http://localhost:11434/api/tags &>/dev/null; then
    echo "⚠️  Ollama is installed but not running. Starting it now..."
    ollama serve &>/dev/null &
    sleep 3
    if ! curl -sf http://localhost:11434/api/tags &>/dev/null; then
        echo ""
        echo "❌ Could not start Ollama. Run manually: ollama serve"
        echo ""
        exit 1
    fi
fi
echo "✅ Ollama is running."

# ── Check model ───────────────────────────────────────────────────────────────
if ! ollama list 2>/dev/null | grep -q "qwen2.5-coder:7b"; then
    echo ""
    echo "⚠️  Model qwen2.5-coder:7b not found. Pulling now (this may take a while)..."
    ollama pull qwen2.5-coder:7b
    echo "✅ Model ready."
fi
echo "✅ Model qwen2.5-coder:7b is available."
echo ""

# ── Find or build jar ─────────────────────────────────────────────────────────
JAR=$(ls target/*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1)

if [ -z "$JAR" ]; then
    echo "🔨 Building project..."
    mvn clean package -DskipTests -q
    echo "✅ Build complete."
    echo ""
    JAR=$(ls target/*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1)
fi

if [ -z "$JAR" ]; then
    echo "❌ Build failed — no jar found in target/."
    echo "   Run manually: mvn clean package -DskipTests"
    exit 1
fi

# ── Run ───────────────────────────────────────────────────────────────────────
echo "============================================"
echo " IS442 AUTO-GRADING SYSTEM — WEB UI MODE"
echo "============================================"
echo ""
echo "Starting web server..."
echo "Open browser at: http://localhost:8080"
echo "Press Ctrl+C to stop."
echo ""

java --enable-native-access=ALL-UNNAMED -jar "$JAR" --spring.profiles.active=web
