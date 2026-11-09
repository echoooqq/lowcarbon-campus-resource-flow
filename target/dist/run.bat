@echo off
chcp 65001 >nul 2>&1
cd /d "%~dp0"

echo ============================================
echo   LowCarbon Campus System
echo ============================================
echo.

set "JAVA_CMD="

REM Search 1: BellSoft Liberica (most common)
if exist "C:\Program Files\BellSoft\LibericaJDK-21-Full\bin\java.exe" (
    set "JAVA_CMD=C:\Program Files\BellSoft\LibericaJDK-21-Full\bin\java.exe"
    echo [INFO] Found JDK 21 at BellSoft
    goto :launch
)

REM Search 2: Any BellSoft
for /d %%d in ("C:\Program Files\BellSoft\*") do (
    if exist "%%d\bin\java.exe" (
        set "JAVA_CMD=%%d\bin\java.exe"
        echo [INFO] Found Java at %%d
        goto :launch
    )
)

REM Search 3: Broad search for recent JDKs
for /d %%d in ("C:\Program Files\*jdk*") do (
    if exist "%%d\bin\java.exe" (
        set "JAVA_CMD=%%d\bin\java.exe"
        echo [INFO] Found Java at %%d
        goto :launch
    )
)
for /d %%d in ("C:\Program Files\*JDK*") do (
    if exist "%%d\bin\java.exe" (
        set "JAVA_CMD=%%d\bin\java.exe"
        echo [INFO] Found Java at %%d
        goto :launch
    )
)

REM Search 4: JavaHome subdirs
for /d %%d in ("C:\Program Files\Java\*") do (
    if exist "%%d\bin\java.exe" (
        set "JAVA_CMD=%%d\bin\java.exe"
        echo [INFO] Found Java at %%d
        goto :launch
    )
)

REM Search 5: PATH java
where java >nul 2>&1
if not errorlevel 1 (
    for /f "delims=" %%j in ('where java ^| findstr /v javapath') do (
        set "JAVA_CMD=%%j"
        echo [INFO] Using system java: %%j
        goto :launch
    )
)

REM Search 6: JAVA_HOME
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
        echo [INFO] Using JAVA_HOME
        goto :launch
    )
)

echo [ERROR] No Java found. Install JDK 21+ from https://adoptium.net/
pause
exit /b 1

:launch
echo [INFO] Java: "%JAVA_CMD%"
echo.

if not exist "lowcarbon-system.jar" (
    echo [ERROR] lowcarbon-system.jar not found
    pause
    exit /b 1
)

"%JAVA_CMD%" -jar lowcarbon-system.jar
if errorlevel 1 (
    echo.
    echo [ERROR] Exit code: %errorlevel%
    pause
)