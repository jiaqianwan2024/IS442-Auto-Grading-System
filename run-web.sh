#!/bin/bash
# ============================================================================
# run-web.sh — IS442 Auto-Grading System (Web UI mode)
#
# WHAT THIS DOES:
#   1. Loads COHERE_API_KEY from .env
#   2. Builds the project if the jar is missing
#   3. Starts the Spring Boot web server on http://localhost:8080
#
# REQUIREMENTS:
#   - .env file: COHERE_API_KEY=your-key  (free at dashboard.cohere.com)
#
# USAGE:
#   chmod +x run-web.sh   (first time only)
#   ./run-web.sh
#   → Open http://localhost:8080 in your browser
#   → Press Ctrl+C to stop
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
echo " IS442 AUTO-GRADING SYSTEM — WEB UI MODE"
echo "============================================"
echo ""
echo "Starting web server..."
echo "Open browser at: http://localhost:8080"
echo "Press Ctrl+C to stop."
echo ""

java --enable-native-access=ALL-UNNAMED -jar "$JAR" --spring.profiles.active=web