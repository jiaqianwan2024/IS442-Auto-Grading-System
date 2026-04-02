#!/bin/bash
# ============================================================================
# run-web.sh - IS442 Auto-Grading System (Web UI mode)
#
# WHAT THIS DOES:
#   1. Clears resources/assessments on startup
#   2. Loads COHERE_API_KEY from .env
#   3. Runs mvn clean install
#   4. Starts the Spring Boot web server on http://localhost:9090
# ============================================================================

set -e

if [ -d "resources/assessments" ]; then
    echo "Clearing resources/assessments..."
    find resources/assessments -mindepth 1 -exec rm -rf {} +
    echo "Assessment folder cleared."
    echo ""
fi

if [ -f .env ]; then
    echo "Loading API key from .env..."
    export $(grep -v '^#' .env | grep -v '^$' | xargs)
else
    echo "No .env file found. Checking environment for COHERE_API_KEY..."
fi

if [ -z "$COHERE_API_KEY" ]; then
    echo ""
    echo "COHERE_API_KEY is not set."
    echo "Create a .env file with: COHERE_API_KEY=your-key-here"
    echo "Get a free key at: https://dashboard.cohere.com/api-keys"
    echo ""
    exit 1
fi

echo "API key found."
echo ""

echo "Running Maven clean install..."
mvn clean install -q || {
    echo ""
    echo "WARNING: Maven install failed. Retrying with clean package..."
    mvn clean package -q
}
echo "Build complete."
echo ""

JAR=$(ls target/*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1)

if [ -z "$JAR" ]; then
    echo "Build failed - no jar found in target/."
    echo "Run manually: mvn clean install or mvn clean package"
    exit 1
fi

echo "============================================"
echo " IS442 AUTO-GRADING SYSTEM - WEB UI MODE"
echo "============================================"
echo ""
echo "Starting web server..."
echo "Open browser at: http://localhost:9090"
echo "Press Ctrl+C to stop."
echo ""

java --enable-native-access=ALL-UNNAMED -jar "$JAR" --spring.profiles.active=web
