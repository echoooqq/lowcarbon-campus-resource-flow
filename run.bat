@echo off
REM ======================================================================
REM 低碳校园资源循环流转桌面系统 - Windows 启动 / 构建脚本
REM 使用项目 tools/ 下自带的 JDK 21 和 Maven，不依赖系统全局环境
REM ======================================================================

setlocal

REM --- 定位项目根目录 ---
set "PROJECT_ROOT=%~dp0"
cd /d "%PROJECT_ROOT%"

REM --- 设置 JDK 21 ---
set "JAVA_HOME=%PROJECT_ROOT%tools\jdk-21"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] 未找到 JDK 21: %JAVA_HOME%\bin\java.exe
    echo         请先运行 install-tools.bat 或手动解压 tools 下的 JDK zip
    pause
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

REM --- 设置 Maven ---
set "MAVEN_HOME=%PROJECT_ROOT%tools\maven-3.9.16"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo [ERROR] 未找到 Maven: %MAVEN_HOME%\bin\mvn.cmd
    echo         请先运行 install-tools.bat 或手动解压 tools 下的 Maven zip
    pause
    exit /b 1
)
set "PATH=%MAVEN_HOME%\bin;%PATH%"

REM --- 打印环境 ---
echo ========================================
echo   低碳校园资源循环流转桌面系统
echo ========================================
echo JAVA_HOME : %JAVA_HOME%
echo MAVEN_HOME: %MAVEN_HOME%
echo.

java -version
mvn -version
echo.

REM --- 执行参数指定的动作 ---
if "%~1"=="" goto :interactive
if /i "%~1"=="compile"  goto :compile
if /i "%~1"=="test"     goto :test
if /i "%~1"=="package"  goto :package
if /i "%~1"=="run"      goto :run
if /i "%~1"=="clean"    goto :clean
echo [WARN] 未知命令: %~1
echo        可用命令: compile  test  package  run  clean
goto :interactive

:compile
echo.
echo --- mvn compile ---
mvn -q compile
goto :eof

:test
echo.
echo --- mvn test ---
mvn test
goto :eof

:package
echo.
echo --- mvn package ---
mvn -q package -DskipTests
echo.
echo 打包完成，产物在 target\lowcarbon-system-jar-with-dependencies.jar
goto :eof

:run
echo.
echo --- run Main ---
java -cp "target\classes;%MAVEN_HOME%\..\repository\org\xerial\sqlite-jdbc\3.45.1.0\sqlite-jdbc-3.45.1.0.jar" com.lowcarbon.Main
goto :eof

:clean
echo.
echo --- mvn clean ---
mvn -q clean
goto :eof

:interactive
echo 可用命令:
echo   run.bat compile   - 编译
echo   run.bat test      - 运行测试
echo   run.bat package   - 打包 fat-jar
echo   run.bat run       - 运行 Main
echo   run.bat clean     - 清理 target
echo.
set /p CMD="请输入命令: "
call "%~f0" %CMD%
goto :eof

endlocal
