@echo off
chcp 65001 >nul
REM ============================================================
REM  PGFPlotsGenerator Java 后端 - 以 Maven 方式启动开发服务（# mvn spring-boot:run 开发启动）
REM  修复：直接执行 mvn spring-boot:run 会报
REM        "RunMojo ... class file version 61.0 ... only recognizes up to 52.0"
REM        原因是 Maven 运行在 JDK 8 上（Spring Boot 3 插件需 JDK 17+）。
REM  本脚本自动挑选 JDK 17+ 后再调用 mvn spring-boot:run。
REM  用法：双击本文件，或在 spring-backend 目录执行  mvn-run.cmd
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

set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [INFO] 使用 JDK: %JAVA_HOME%
echo [INFO] 配置：自动复用 ..\backend\.env（若存在）
echo [INFO] 执行 mvn spring-boot:run ...（端口 3000）

call mvn -B spring-boot:run %*
endlocal
