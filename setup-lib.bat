@echo off
REM setup-lib.bat
REM Run this ONCE after cloning to download all required jars into lib/
REM Uses curl which is built into Windows 10+ (no extra tools needed)

echo.
echo ============================================================
echo  IS442 Auto-Grading System -- Dependency Setup
echo ============================================================
echo.

if not exist lib mkdir lib

set M=https://repo1.maven.org/maven2

echo Downloading jars into lib\ ...
echo.

call :get poi-5.2.3.jar                  %M%/org/apache/poi/poi/5.2.3/poi-5.2.3.jar
call :get poi-ooxml-5.2.3.jar            %M%/org/apache/poi/poi-ooxml/5.2.3/poi-ooxml-5.2.3.jar
call :get poi-ooxml-full-5.2.3.jar       %M%/org/apache/poi/poi-ooxml-full/5.2.3/poi-ooxml-full-5.2.3.jar
call :get xmlbeans-5.1.1.jar             %M%/org/apache/xmlbeans/xmlbeans/5.1.1/xmlbeans-5.1.1.jar
call :get commons-compress-1.21.jar      %M%/org/apache/commons/commons-compress/1.21/commons-compress-1.21.jar
call :get commons-collections4-4.4.jar   %M%/org/apache/commons/commons-collections4/4.4/commons-collections4-4.4.jar
call :get commons-codec-1.15.jar         %M%/commons-codec/commons-codec/1.15/commons-codec-1.15.jar
call :get commons-io-2.11.0.jar          %M%/commons-io/commons-io/2.11.0/commons-io-2.11.0.jar
call :get curvesapi-1.07.jar             %M%/com/github/virtuald/curvesapi/1.07/curvesapi-1.07.jar
call :get log4j-api-2.20.0.jar           %M%/org/apache/logging/log4j/log4j-api/2.20.0/log4j-api-2.20.0.jar
call :get log4j-core-2.20.0.jar          %M%/org/apache/logging/log4j/log4j-core/2.20.0/log4j-core-2.20.0.jar

echo.
echo ============================================================
echo  Done! Now run:
echo    compile.bat
echo    run.bat
echo ============================================================
echo.
goto :eof

:get
if exist lib\%1 (
    echo [SKIP]     %1 already exists
) else (
    echo [GET]      %1
    curl -L --silent --show-error -o lib\%1 %2
    if errorlevel 1 (
        echo [ERROR]    Failed to download %1 - check internet connection
    )
)
goto :eof