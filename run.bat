@echo off
chcp 65001 >nul 2>&1
cd /d "%~dp0"

echo ============================================
echo   LowCarbon Campus System
echo ============================================
echo.

REM find jar dir
set "DIST_DIR=%~dp0target\dist"
if exist "lowcarbon-system.jar" set "DIST_DIR=%~dp0"

if not exist "%DIST_DIR%\lowcarbon-system.jar" (
    echo [ERROR] lowcarbon-system.jar not found in root or target\dist\
    echo         Check GitHub Releases or run target\dist\run.bat
    pause
    exit /b 1
)

REM find java
set "JAVA_CMD="

REM 1) BellSoft JDK 21 (hardcoded, no-spaces via 8.3 short path)
if exist "C:\Progra~1\BellSoft\Liberic~1\bin\java.exe" set "JAVA_CMD=C:\Progra~1\BellSoft\Liberic~1\bin\java.exe"
if not defined JAVA_CMD (
    for /d %%d in ("C:\Progra~1\BellSoft\*") do (
        if exist "%%d\bin\java.exe" if not defined JAVA_CMD set "JAVA_CMD=%%d\bin\java.exe"
    )
)

REM 2) JAVA_HOME (but filter out old Java 8)
if not defined JAVA_CMD if not "%JAVA_HOME%"=="" (
    if exist "%JAVA_HOME%\bin\java.exe" (
        for /f "tokens=3" %%v in ('"%JAVA_HOME%\bin\java.exe" -version 2^>^&1 ^| findstr version') do (
            echo %%v | findstr /r "1\.[89]\." >nul 2>&1
            if errorlevel 1 set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
        )
    )
)

REM 3) where java (same filter)
if not defined JAVA_CMD (
    where java >nul 2>&1
    if not errorlevel 1 for /f "delims=" %%j in ('where java') do (
        if not defined JAVA_CMD (
            for /f "tokens=3" %%v in ('"%%j" -version 2^>^&1 ^| findstr version') do (
                echo %%v | findstr /r "1\.[89]\." >nul 2>&1
                if errorlevel 1 set "JAVA_CMD=%%j"
            )
        )
    )
)

if not defined JAVA_CMD (
    echo [ERROR] No Java 21+. Install from https://adoptium.net/
    pause
    exit /b 1
)

echo [INFO] Java : %JAVA_CMD%
echo [INFO] Jar  : %DIST_DIR%\lowcarbon-system.jar
echo.
echo Starting GUI...
echo.

cd /d "%DIST_DIR%"
"%JAVA_CMD%" -jar lowcarbon-system.jar
if errorlevel 1 (
    echo.
    echo [ERROR] Exit code: %errorlevel%
    pause
)
