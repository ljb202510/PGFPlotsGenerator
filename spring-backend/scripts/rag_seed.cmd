@echo off
REM [RAG] seed: rebuild chart template library (idempotent)
cd /d "%~dp0.."
call mvn -q -DskipTests package
if errorlevel 1 exit /b 1
java -jar target/pgfplots-backend-1.0.0.jar --rag-cli=seed
