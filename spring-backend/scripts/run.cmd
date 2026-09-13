@echo off
chcp 65001 >nul
REM ============================================================
REM  PGFPlotsGenerator Java 后端 - 一键启动
REM  1) 自动挑选 JDK 17+（避免 Maven 用 JDK 8）
REM  2) 若 jar 不存在则先构建
REM  3) 启动服务（默认端口 3000，可直接对接现有 Vue 前端）
REM  配置来源：自动复用 hello/backend/.env（DB/密钥/SMTP）
REM  用法：双击本文件，或在 spring-backend 目录执行  run.cmd
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
  echo [ERROR] 未找到 JDK 17+。请安装 JDK 17/21，或设置 PG_JAVA_HOME 后重试。
  exit /b 1
)

if not exist "target\pgfplots-backend-1.0.0.jar" (
  echo [INFO] 未找到 jar，先执行构建...
  call "%~dp0build.cmd"
  if errorlevel 1 exit /b 1
)

echo [INFO] 使用 JDK: %JAVA_HOME%
echo [INFO] 配置：自动复用 ..\backend\.env（若存在）
echo [INFO] 启动中... 端口 3000
"%JAVA_HOME%\bin\java.exe" -jar "target\pgfplots-backend-1.0.0.jar"
endlocal
