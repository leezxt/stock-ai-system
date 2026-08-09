@echo off
setlocal
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-no-secrets.ps1 %*
exit /b %errorlevel%
