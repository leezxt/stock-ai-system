@echo off
cd /d "%~dp0.."
start "Stock AI Mock API" cmd /k node tools\mock-api-server.mjs
start "" "%cd%\frontend\index.html"
