@echo off
REM 一键冒烟验证：启动后端 → 调用真实接口 → 输出 PASS/FAIL
REM 参数原样透传给 verify.js（如 --no-start）# 冒烟测试
cd /d "%~dp0.."
node "%~dp0verify.js" %*
