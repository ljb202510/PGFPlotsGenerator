@echo off
REM ============================================================
REM  PGFPlotsGenerator Java 后端 - 一键构建（打包 jar）
REM  作用：自动挑选 JDK 17+ 作为 JAVA_HOME（避免 Maven 用 JDK 8 导致
REM        "无效的标记: --release"），然后执行 mvn clean package。
REM  用法：双击本文件，或在 spring-backend 目录执行  build.cmd
REM ============================================================
setlocal
cd /d "%~dp0.."

set "JAVA_HOME="
if defined PG_JAVA_HOME if exist "%PG_JAVA_HOME%\bin\javac.exe" set "JAVA_HOME=%PG_JAVA_HOME%"

if not defined JAVA_HOME (
  for %%P in (
    "C:\Program Files\Microsoft\jdk-21.0.2.13-hotspot"
    "C:\Program Files\Java\jdk-21"
    "C:\Program Files\Java\jdk-21.0.2"
    "C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot"
    "C:\Program Files\Java\jdk-17"
    "C:\Program Files\Java\jdk-23"
  ) do (
    if not defined JAVA_HOME if exist "%%~P\bin\javac.exe" set "JAVA_HOME=%%~P"
  )
)

if not defined JAVA_HOME (
  echo [ERROR] 未找到 JDK 17+。
  echo         请安装 JDK 17 或 21，然后重试；
  echo         或先执行  set "PG_JAVA_HOME=C:\路径\到\jdk"  再运行本脚本。
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [INFO] 使用 JDK: %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version

call mvn -B -DskipTests clean package
if errorlevel 1 (
  echo [ERROR] 构建失败。
  exit /b 1
)

echo.
echo [OK] 构建成功: %~dp0target\pgfplots-backend-1.0.0.jar
endlocal
exit /b 0
