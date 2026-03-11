@echo off
echo ============================================
echo  IS442 AUTO-GRADING SYSTEM — WEB UI MODE
echo ============================================
echo.
echo Starting Spring Boot web application...
echo Open browser at: http://localhost:8080
echo Press Ctrl+C to stop the server.
echo.
mvn spring-boot:run -Dspring-boot.run.profiles=web
