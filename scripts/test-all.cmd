@echo off
cd /d "%~dp0.."
call scripts\check-no-secrets.cmd
if errorlevel 1 exit /b %errorlevel%
node tools\smoke-test.mjs
