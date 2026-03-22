#!/bin/bash
# ============================================================================
# run-cli.sh — IS442 Auto-Grading System (CLI mode)
#
# WHAT THIS DOES (in order):
#   1. Loads COHERE_API_KEY from .env
#   2. Builds the project if the jar is missing
#   3. Runs the full pipeline:
#        a. Parse exam paper PDF for mark weights  (resources/input/exam/*.pdf)
#        b. Generate *Tester.java via Cohere LLM   (resources/input/testers/)
#        c. Grade all student submissions
#        d. Export score sheet
#
# REQUIREMENTS:
#   - .env file:  COHERE_API_KEY=your-key  (free at dashboard.cohere.com)
#   - resources/input/submissions/student-submission.zip
#   - resources/input/template/RenameToYourUsername.zip
#   - resources/input/exam/exam-paper.pdf       (required — for mark weights)
#   - config/IS442-ScoreSheet.csv
#
# OUTPUT:
#   resources/output/reports/IS442-ScoreSheet-Updated.xlsx
#   resources/output/reports/IS442-Statistics.xlsx
#   resources/output/reports/IS442-Plagiarism-Report.xlsx
# ============================================================================

set -e

# ── Load .env ─────────────────────────────────────────────────────────────────
if [ -f .env ]; then
    echo "🔑 Loading API key from .env..."
    export $(grep -v '^#' .env | grep -v '^$' | xargs)
else
    echo "⚠️  No .env file found. Checking environment for COHERE_API_KEY..."
fi

if [ -z "$COHERE_API_KEY" ]; then
    echo ""
    echo "❌ COHERE_API_KEY is not set."
    echo "   Create a .env file with: COHERE_API_KEY=your-key-here"
    echo "   Get a free key at: https://dashboard.cohere.com/api-keys"
    echo ""
    exit 1
fi
echo "✅ API key found."
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