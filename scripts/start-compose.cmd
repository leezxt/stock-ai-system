@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

if /I "%~1"=="/?" goto usage
if /I "%~1"=="--help" goto usage

rem Load local-only keys and provider settings without ever printing their values.
if exist "%~dp0..\local-env.cmd" call "%~dp0..\local-env.cmd"

if not defined STOCKAI_RAG_EMBEDDING_PROVIDER set "STOCKAI_RAG_EMBEDDING_PROVIDER=hash"
set "embeddingProvider=%STOCKAI_RAG_EMBEDDING_PROVIDER%"

if /I "%embeddingProvider%"=="hash" goto provider_ok
if /I "%embeddingProvider%"=="openai" goto openai_provider
echo [start-compose] ERROR: STOCKAI_RAG_EMBEDDING_PROVIDER must be hash or openai.
exit /b 2

:openai_provider
rem Keep the embedding key explicit, but accept OPENAI_API_KEY as the documented local shortcut.
if not defined STOCKAI_RAG_EMBEDDING_API_KEY if defined OPENAI_API_KEY set "STOCKAI_RAG_EMBEDDING_API_KEY=%OPENAI_API_KEY%"
if not defined STOCKAI_RAG_EMBEDDING_API_KEY (
  echo [start-compose] ERROR: OpenAI semantic RAG is selected but no embedding key is configured.
  echo [start-compose] Set STOCKAI_RAG_EMBEDDING_API_KEY or OPENAI_API_KEY in local-env.cmd, then run this command again.
  exit /b 2
)

:provider_ok
if not defined STOCK_AI_HOST_PORT set "STOCK_AI_HOST_PORT=18080"
if not defined STOCK_AI_POSTGRES_PORT set "STOCK_AI_POSTGRES_PORT=5432"
echo [start-compose] embedding provider: %embeddingProvider%
echo [start-compose] app URL: http://localhost:%STOCK_AI_HOST_PORT%/app
echo [start-compose] PostgreSQL host port: %STOCK_AI_POSTGRES_PORT%
if /I "%embeddingProvider%"=="openai" echo [start-compose] semantic RAG selected; re-import documents after switching provider.

docker compose up --build %*
set "exitCode=%ERRORLEVEL%"
exit /b %exitCode%

:usage
echo Usage: scripts\start-compose.cmd [-d]
echo.
echo Loads ignored local-env.cmd, validates the selected RAG provider, and starts Docker Compose.
echo hash is the offline default. openai requires STOCKAI_RAG_EMBEDDING_API_KEY or OPENAI_API_KEY.
exit /b 0
