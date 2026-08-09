@echo off
setlocal
cd /d "%~dp0.."
if exist "local-env.cmd" call "local-env.cmd"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-live-config.ps1
set "configExit=%ERRORLEVEL%"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-all-live.ps1
set "smokeExit=%ERRORLEVEL%"

rem Preserve a real smoke failure over a configuration block; otherwise
rem return the preflight block so CI/local callers cannot treat it as ready.
if not "%smokeExit%"=="0" exit /b %smokeExit%
exit /b %configExit%
