@echo off
REM run.bat

setlocal enabledelayedexpansion

set CP=bin
for %%f in (lib\*.jar) do set CP=!CP!;%%f

java -cp "!CP!" com.autogradingsystem.Main