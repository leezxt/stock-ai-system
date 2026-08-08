@echo off
rem Copy this file to local-env.cmd and put real keys there.
rem local-env.cmd is ignored by git.
set "OPENAI_API_KEY="
set "OPENAI_MODEL=gpt-5.5"
rem Optional semantic RAG embeddings. Keep hash for offline mode; switching to openai requires re-importing documents.
rem if not defined STOCKAI_RAG_EMBEDDING_PROVIDER set "STOCKAI_RAG_EMBEDDING_PROVIDER=openai"
rem if not defined STOCKAI_RAG_EMBEDDING_API_KEY if defined OPENAI_API_KEY set "STOCKAI_RAG_EMBEDDING_API_KEY=%OPENAI_API_KEY%"
rem set "OPENAI_EMBEDDING_MODEL=text-embedding-3-small"
rem set "MIMO_API_KEY="
rem set "MIMO_MODEL=mimo-v2.5-pro"
rem set "MIMO_CHAT_COMPLETIONS_URL=https://api.xiaomimimo.com/v1/chat/completions"
rem set "STOCK_AI_BASE_URL=http://localhost:8080/api/v1"
rem set "STOCKAI_AUTH_SECRET=replace-with-at-least-32-random-characters"
rem set "STOCKAI_SECRETS_ENCRYPTION_KEY=replace-with-a-separate-random-secret"
rem Optional Google Identity Services client ID (public value; Google login stays disabled when blank).
rem set "STOCKAI_GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com"
rem set "ALPHAVANTAGE_API_KEY="
rem set "FINMIND_API_TOKEN="
rem set "FINMIND_BASE_URL=https://api.finmindtrade.com/api/v4"
rem set "FMP_API_KEY="
rem set "STOCKAI_TWSE_DISCLOSURE_URL=https://your-json-endpoint.example/disclosures?symbol={symbol}&limit={limit}"
rem Optional local-only watchlist alert evaluator. Keep disabled until provider call cost is understood.
rem set "STOCKAI_WATCHLIST_ALERTS_SCHEDULER_ENABLED=false"
rem set "STOCKAI_WATCHLIST_ALERTS_SCHEDULER_FIXED_DELAY_MS=900000"
rem set "STOCKAI_WATCHLIST_ALERTS_SCHEDULER_INITIAL_DELAY_MS=60000"
rem set "STOCKAI_WATCHLIST_ALERTS_NOTIFICATION_MODE=LOCAL_ONLY"
rem Optional PostgreSQL + pgvector persistence. Leave blank to use local file/in-memory fallback.
rem set "STOCKAI_DATABASE_URL=jdbc:postgresql://localhost:5432/stock_ai"
rem set "STOCKAI_DATABASE_USERNAME=stock_ai"
rem set "STOCKAI_DATABASE_PASSWORD=stock_ai"
