@echo off
REM [RAG] purge: delete polluted history vectors (charts with duplicated multi-series data)
cd /d "%~dp0.."
call mvn -q -DskipTests package
if errorlevel 1 exit /b 1
java -jar target/pgfplots-backend-1.0.0.jar --rag-cli=purge
