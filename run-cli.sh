#!/bin/bash
# ============================================================================
# run-cli.sh — IS442 Auto-Grading System (CLI mode)
#
# WHAT THIS DOES (in order):
#   1. Checks that Ollama is running (required for AI test case generation)
#   2. Builds the project if the jar is missing
#   3. Runs the full pipeline:
#        a. Parse exam paper PDF for mark weights  (resources/input/exam/*.pdf)
#        b. Generate *Tester.java via qwen2.5-coder:7b  (resources/input/testers/)
#        c. Grade all student submissions
#        d. Export score sheet
#
# REQUIREMENTS:
#   - Ollama installed and running: https://ollama.com/download
#   - Model pulled: ollama pull qwen2.5-coder:7b
#   - resources/input/submissions/student-submission.zip
#   - resources/input/template/RenameToYourUsername.zip
#   - resources/input/exam/exam-paper.pdf
#   - config/IS442-ScoreSheet.csv
#
# OUTPUT:
#   resources/output/reports/IS442-ScoreSheet-Updated.xlsx
#   resources/output/reports/IS442-Statistics.xlsx
#   resources/output/reports/IS442-Plagiarism-Report.xlsx
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
echo " IS442 AUTO-GRADING SYSTEM — CLI MODE"
echo "============================================"
echo ""
echo "Tester generation runs automatically (Phase 3)."
echo "No web server will start. Output shown below."
echo ""

java --enable-native-access=ALL-UNNAMED -jar "$JAR" --spring.profiles.active=cli

echo ""
echo "✅ Done. Reports are in: resources/output/reports/"
