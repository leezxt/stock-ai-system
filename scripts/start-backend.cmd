@echo off
setlocal
cd /d "%~dp0..\backend"
if exist "%~dp0..\local-env.cmd" call "%~dp0..\local-env.cmd"
if exist "C:\Users\lee\.jdks\ms-21.0.10\bin\java.exe" (
  set "JAVA_HOME=C:\Users\lee\.jdks\ms-21.0.10"
  set "PATH=%JAVA_HOME%\bin;%PATH%"
)
echo [start-backend] backend dir: %CD%
if defined OPENAI_MODEL echo [start-backend] OPENAI_MODEL=%OPENAI_MODEL%
if defined STOCK_AI_BASE_URL echo [start-backend] STOCK_AI_BASE_URL=%STOCK_AI_BASE_URL%
mvn spring-boot:run
