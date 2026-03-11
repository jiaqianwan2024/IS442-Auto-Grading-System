@echo off
REM fetch-deps.bat
REM Run this ONCE to download all Spring Boot JARs into lib\
REM Requires Maven to be installed (mvn -version to check)

echo Downloading Spring Boot dependencies into lib\ ...
echo This may take a minute on first run.
echo.

mvn dependency:copy-dependencies -DoutputDirectory=lib -DincludeScope=runtime

if %ERRORLEVEL% == 0 (
    echo.
    echo Done! All JARs are now in lib\
    echo You can now run compile.bat and run.bat as normal.
) else (
    echo.
    echo Failed. Make sure Maven is installed: https://maven.apache.org/download.cgi
    echo Then add Maven's bin\ folder to your PATH and try again.
)

pause
