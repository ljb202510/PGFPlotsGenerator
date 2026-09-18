@echo off
chcp 65001 >/dev/null
REM ============================================================
REM  PGFPlotsGenerator Java backend - one-click start
REM  1) auto-pick JDK 17+ (Maven on JDK 8 fails Spring Boot 3)
REM  2) build first if jar is missing
REM  3) start service (default port 3000, works with Vue frontend)
REM  Config: auto-import spring-backend/.env (DB / SMTP / LLM keys)
REM  Usage: double-click, or run  scripts\run.cmd  from spring-backend
REM  NOTE: keep this file ASCII-only; cmd.exe misparses UTF-8 batch
REM        after "chcp 65001" and may run comment text as commands.
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
  echo [ERROR] JDK 17+ not found. Install JDK 17/21 or set PG_JAVA_HOME.
  exit /b 1
)

if not exist "target\pgfplots-backend-1.0.0.jar" (
  echo [INFO] jar not found, building first...
  call "%~dp0build.cmd"
  if errorlevel 1 exit /b 1
)

echo [INFO] Using JDK: %JAVA_HOME%
echo [INFO] Config: auto-import spring-backend/.env ^(if present^)
echo [INFO] Starting... port 3000
"%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -jar "target\pgfplots-backend-1.0.0.jar"
endlocal
