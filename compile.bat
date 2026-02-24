@echo off
REM compile.bat

REM Clean previous compilation
rmdir /s /q bin 2>nul
mkdir bin

REM Build classpath with all jars in lib/
set CP=src\main\java
for %%f in (lib\*.jar) do set CP=!CP!;%%f

REM Enable delayed expansion for CP variable
setlocal enabledelayedexpansion

set CP=src\main\java
for %%f in (lib\*.jar) do set CP=!CP!;%%f

REM Compile all files
javac -d bin -cp "!CP!" ^
  src\main\java\com\autogradingsystem\PathConfig.java ^
  src\main\java\com\autogradingsystem\model\*.java ^
  src\main\java\com\autogradingsystem\extraction\model\*.java ^
  src\main\java\com\autogradingsystem\discovery\model\*.java ^
  src\main\java\com\autogradingsystem\extraction\service\*.java ^
  src\main\java\com\autogradingsystem\discovery\service\*.java ^
  src\main\java\com\autogradingsystem\execution\service\*.java ^
  src\main\java\com\autogradingsystem\analysis\service\*.java ^
  src\main\java\com\autogradingsystem\extraction\controller\*.java ^
  src\main\java\com\autogradingsystem\discovery\controller\*.java ^
  src\main\java\com\autogradingsystem\execution\controller\*.java ^
  src\main\java\com\autogradingsystem\analysis\controller\*.java ^
  src\main\java\com\autogradingsystem\Main.java

echo Compilation complete!