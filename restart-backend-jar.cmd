@echo off
setlocal
cd /d "%~dp0"
if exist "%~dp0local-env.cmd" call "%~dp0local-env.cmd"
if exist "C:\Users\lee\.jdks\ms-21.0.10\bin\java.exe" (
  set "JAVA_HOME=C:\Users\lee\.jdks\ms-21.0.10"
  set "PATH=%JAVA_HOME%\bin;%PATH%"
)
cd /d "%~dp0backend"
java -jar "target\stock-ai-backend-0.1.0-SNAPSHOT.jar"
