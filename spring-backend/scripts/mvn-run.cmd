@echo off
chcp 65001 >/dev/null
REM ============================================================
REM  PGFPlotsGenerator Java backend - dev mode (mvn spring-boot:run)
REM  Why this wrapper: plain "mvn spring-boot:run" may fail with
REM    "RunMojo ... class file version 61.0 ... only recognizes up to 52.0"
REM    when Maven runs on JDK 8 (Spring Boot 3 plugin needs JDK 17+).
REM  This script auto-picks JDK 17+, forces UTF-8 console IO, then runs Maven.
REM  Usage: double-click, or run  scripts\mvn-run.cmd  from spring-backend
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

set "PATH=%JAVA_HOME%\bin;%PATH%"
REM Force UTF-8 for the Maven JVM itself: it relays the forked app's output
REM through its own stdout; on a GBK default it would re-encode Chinese logs.
if not defined MAVEN_OPTS set "MAVEN_OPTS="
set "MAVEN_OPTS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 %MAVEN_OPTS%"
echo [INFO] Using JDK: %JAVA_HOME%
echo [INFO] Config: auto-import spring-backend/.env ^(if present^)
echo [INFO] Running mvn spring-boot:run ... (port 3000)

call mvn -B spring-boot:run %*
endlocal
