@echo off
rem Copy this file to local-env.cmd and put real keys there.
rem local-env.cmd is ignored by git.
set "OPENAI_API_KEY="
set "OPENAI_MODEL=gpt-5.5"
rem set "STOCK_AI_BASE_URL=http://localhost:8080/api/v1"
rem set "STOCKAI_AUTH_SECRET=replace-with-at-least-32-random-characters"
rem set "STOCKAI_SECRETS_ENCRYPTION_KEY=replace-with-a-separate-random-secret"
rem set "ALPHAVANTAGE_API_KEY="
rem set "FINMIND_API_TOKEN="
rem set "FINMIND_BASE_URL=https://api.finmindtrade.com/api/v4"
rem set "FMP_API_KEY="
rem set "STOCKAI_TWSE_DISCLOSURE_URL=https://your-json-endpoint.example/disclosures?symbol={symbol}&limit={limit}"
rem Optional PostgreSQL + pgvector persistence. Leave blank to use local file/in-memory fallback.
rem set "STOCKAI_DATABASE_URL=jdbc:postgresql://localhost:5432/stock_ai"
rem set "STOCKAI_DATABASE_USERNAME=stock_ai"
rem set "STOCKAI_DATABASE_PASSWORD=stock_ai"
