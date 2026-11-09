@echo off
REM LowCarbon Campus System - Launcher

chcp 65001 >nul
cd /d "%~dp0"

echo ============================================
echo   LowCarbon Campus System
echo ============================================
echo.

if not exist "lowcarbon-system.jar" (
    echo [ERROR] lowcarbon-system.jar not found
    pause
    exit /b 1
)

echo Starting...
echo.

java -jar lowcarbon-system.jar

if errorlevel 1 (
    echo.
    echo [ERROR] Exit code: %errorlevel%
    echo Please make sure JDK 21+ is installed
    pause
)