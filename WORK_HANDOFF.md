# Stock AI Work Handoff

Last updated: 2026-07-10

## Run

```powershell
scripts\start-spring-dashboard.cmd
```

Docker Compose:

```powershell
docker compose up --build -d
```

Open:

```text
http://localhost:8080/app
```

Docker Compose default host URL:

```text
http://localhost:18080/app
```

Stop backend:

```powershell
scripts\stop-backend.cmd
```

## Current State

- Frontend is served by Spring Boot at `/app`.
- API base URL is `http://localhost:8080/api/v1`.
- Docker Compose now packages backend + frontend into one container and publishes by default on `http://localhost:18080`.
- Backend runs on Java 21 on this machine: `C:\Users\lee\.jdks\ms-21.0.10`.
- Stock data has mock fallback, optional Alpha Vantage US daily support, optional TWSE OpenAPI daily close support, and frontend fallback notices.
- AI analysis has mock fallback, optional OpenAI Responses API support, optional Gemini support, and frontend fallback notices.
- OpenAI model defaults to `gpt-5.5`; override with `OPENAI_MODEL` or `stockai.openai.model`. Gemini also supports request-header or account-stored key flow.
- `scripts\start-backend.cmd`, `scripts\smoke-openai-live.cmd`, and `scripts\smoke-account-settings.cmd` load ignored `local-env.cmd` when present; copy `local-env.example.cmd` to set local API keys without committing secrets.
- `local-env.example.cmd` also includes optional `STOCK_AI_BASE_URL` for smoke testing against a non-default backend URL.
- Auth MVP is available with `/api/v1/auth/register`, `/api/v1/auth/login`, and `/api/v1/auth/me`.
- Logged-in users can persist `preferredProvider`, `OpenAI API key`, and `Gemini API key` via `/api/v1/account/settings`.
- AI key resolution order is: request header -> logged-in account settings -> backend env/config fallback.
- Watchlist persists to `backend\data\watchlist.txt` for guest mode and `backend\data\watchlist-*.txt` per account.
- Account settings persist to `backend\data\account-settings\*.properties`.

## Verified

```powershell
cd backend
mvn test
```

Current result: 42 tests passed.

Browser verification:

- `http://localhost:8080/app` loads.
- Real mode can load `AAPL`.
- Analysis label shows `OPENAI / mock-ai` when no OpenAI key is configured.
- Frontend shows a visible Real mode source panel with separate market and AI source status, plus the next live-fix hint.
- `GET /api/v1/health` returns provider readiness without exposing secrets.
- Settings API status table shows status pills for Market Source, AI Source, OpenAI Configured, Alpha Vantage Key, TWSE Endpoint, and the next live-readiness action.
- Settings footer and risk disclaimer no longer claim all data is mock-only; they now mention mock, live provider, and fallback sources.
- Real mode stock load and AI Chat now show compact loading states and prevent duplicate clicks while a request is running.
- Search and watchlist flow now supports Enter-to-search, clearer add/remove feedback, and showing the current real-loaded symbol inside watchlist rows.
- Logged-in settings now show whether OpenAI / Gemini keys are stored in backend account settings, and save account-scoped provider preference from the Settings tab.
- Settings `API 狀態` 現在可直接在頁面內刷新 live readiness，並執行 OpenAI / source-fetch live 驗收；source-fetch 驗收目前涵蓋 US news、TW news、TW financials、transcripts、announcements，缺 key / URL 時會顯示 skip 原因。
- AI analysis panel now groups trend, score, probability, reasons, risks, and conclusion into separate readable blocks.
- Backtest panel now shows rule summary, data source, result state, and real-to-mock fallback status.
- Settings panel now shows an OpenAI live smoke hint with the exact next command based on current readiness.
- `scripts\smoke-account-settings.cmd` now covers register, login, load empty settings, save provider + keys, partial update, clear-key behavior, and reload verification.
- user-scoped watchlist and account-settings files now use SHA-256 filenames for new writes while still reading legacy `hashCode()` filenames if they already exist.
- file-backed persistence now writes through atomic temp-file replacement for users, watchlists, and account settings.
- API contract docs now explicitly call out the `source` fields and `/health.providers` fields that the frontend depends on.
- Startup scripts now avoid double-starting a healthy backend and `stop-backend.cmd` reports clearly when 8080 is already free.
- AI Chat response includes `[mock-ai]`.
- `scripts\smoke-openai-live.cmd` safely skips when `OPENAI_API_KEY` is not set, and was verified to load ignored `local-env.cmd` with a temporary dummy key; live `openai-responses` still needs the real key and backend restart.
- TWSE parser test passes. Runtime on this machine currently falls back to mock because `https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL` fails during local SSL/TLS handshake.
- Rechecked `GET /api/v1/stocks/TW/2330/summary`: backend returns `source: mock`, so TWSE fallback is working.
- Full smoke test rerun on 2026-07-06: `scripts\test-all.cmd` passed, frontend script parse passed, and `scripts\stop-backend.cmd` cleanly reported the no-listener path.
- MVP delivery check completed on 2026-07-06. Remaining work is live-provider completion and post-MVP RAG phase work.
- RAG step 56 completed: backend now has `rag.DocumentType` and `rag.DocumentChunk` with symbol normalization and required-field validation. `mvn test` passed with 17 tests.
- RAG step 57 completed: backend now has `rag.DocumentImportRequest` and `rag.DocumentIngestionService` for import, clean, and chunk. `mvn test` passed with 19 tests.
- RAG step 58 completed: backend now has `rag.VectorStore` plus `rag.InMemoryVectorStore`, `VectorDocument`, `VectorSearchQuery`, and `VectorSearchHit`. `mvn test` passed with 21 tests.
- RAG step 59 completed: backend now has `rag.EmbeddingModel`, `rag.HashEmbeddingModel`, and `rag.DocumentEmbeddingService`. `mvn test` passed with 23 tests.
- RAG step 60 completed: backend now has `rag.DocumentRetriever`, `DocumentRetrieveRequest`, and `RetrievedDocument` on top of the embedding + vector store path. `mvn test` passed with 24 tests.
- RAG step 61 completed: backend now has `stock.RagContextService` and `stock.RagContext`, which assemble technical summary, prediction output, and retrieved document evidence into one prompt context for AI analysis and chat. `/api/v1/ai/analysis`, `/ai/model-comparison`, `/ai/chat`, and `/backtest` now all read through the same RAG composition layer. `mvn test` passed with 26 tests.
- RAG step 62 completed: backend now exposes `POST /api/v1/documents/import` and `POST /api/v1/documents/retrieve` through `rag.RagDocumentController`. Import requests now run ingestion -> embedding -> vector upsert in one path, and retrieval can be checked directly through the API. `mvn test` passed with 27 tests.
- RAG step 63 completed: frontend `Settings` tab now includes a minimal document import panel for market, symbol, doc type, title, source, published time, and content. It calls `POST /api/v1/documents/import`, supports current-symbol autofill and sample seed content, and stores the last import result in local settings. Frontend script parse passed.
- RAG step 64 completed: frontend now calls `POST /api/v1/documents/retrieve` in Real mode and shows retrieved evidence blocks in both analysis and AI Chat panels. Stock load, provider refresh, and chat question all refresh evidence for the current symbol/query. Frontend script parse passed.
- RAG step 65 completed: `/api/v1/ai/analysis` and `/api/v1/ai/chat` now return `evidence` directly in the response body. Frontend now prefers response-embedded evidence and only falls back to `POST /api/v1/documents/retrieve` when evidence is absent. `mvn test` passed with 28 tests and frontend script parse passed.
- RAG step 66 completed: evidence UI is now clearer in analysis, chat, and model-comparison views. Empty states distinguish Mock mode vs missing imports, evidence cards show doc type/symbol/market tags, and Compare now explicitly shows the shared retrieved context used across models. Frontend script parse passed.
- RAG step 67 completed: backend now has source adapters and import routes for `POST /api/v1/documents/source/news/fetch`, `POST /api/v1/documents/source/announcements/fetch`, and `POST /api/v1/documents/source/transcripts/fetch`. US news uses Alpha Vantage `NEWS_SENTIMENT`, US earnings-call transcripts use Alpha Vantage `EARNINGS_CALL_TRANSCRIPT`, and TW company announcements now have a configurable JSON adapter via `stockai.twse.disclosure-url` with `{symbol}` / `{limit}` placeholders. `mvn test` passed with 32 tests.
- RAG step 68 completed: frontend `Settings` tab now exposes source-fetch controls for news, TW financials, company announcements, and earnings transcripts. Users can trigger source ingestion from the UI without manual API calls, and the panel tracks the latest source-import status. Frontend script parse passed.
- RAG step 69 completed: `/api/v1/health` now reports source-fetch readiness for Alpha Vantage news, FinMind TW news, FinMind TW financials, Alpha Vantage transcripts, and TW disclosure ingestion. Frontend `Settings` surfaces those readiness rows so the missing live prerequisites are visible before source import is attempted. `mvn test` passed with 32 tests and frontend script parse passed.
- RAG step 70 prep completed: added `scripts\smoke-source-fetch-live.cmd` and `scripts\smoke-source-fetch-live.ps1`, plus `STOCKAI_TWSE_DISCLOSURE_URL` to `local-env.example.cmd`. Source smoke now covers US news, TW news, TW financials, transcripts, and TW announcements. Current live verification is blocked because `local-env.cmd` does not exist and backend is not reachable at `http://localhost:8080/api/v1`.
- Account settings step completed on 2026-07-10: backend now has `AccountSettingsService` and `/api/v1/account/settings`, frontend now saves logged-in AI keys to backend instead of localStorage, and adapters resolve account-stored keys when request headers are absent. `mvn test` passed with 39 tests and frontend script parse passed.
- Persistence hardening step completed on 2026-07-10: `UserScopedFileLocator` now generates SHA-256 user-scoped filenames for watchlist and account settings, while load paths remain backward-compatible with legacy `hashCode()` filenames. `mvn test` passed with 41 tests and `scripts\\smoke-account-settings.ps1` passed.
- Atomic write step completed on 2026-07-10: `AtomicFileWriter` now handles temp-file + replace writes for `UserStore`, `WatchlistService`, and `AccountSettingsService`. Added `UserStoreTest`. `mvn test` passed with 42 tests and `scripts\\smoke-account-settings.ps1` passed.
- Docker Compose step completed on 2026-07-10: added root `Dockerfile`, `compose.yaml`, and `.dockerignore`. Verified `docker compose up --build -d`, `GET http://localhost:18080/api/v1/health`, and `GET http://localhost:18080/app`.

## Next Development Order

1. Start backend and run `scripts\smoke-account-settings.cmd` for runtime verification of auth + account settings.
2. Copy `local-env.example.cmd` to `local-env.cmd`, set `OPENAI_API_KEY`, restart backend, then run `scripts\smoke-openai-live.cmd` to verify live `openai-responses`.
3. Continue with source verification: set `ALPHAVANTAGE_API_KEY` and optional `STOCKAI_TWSE_DISCLOSURE_URL`, then run `scripts\smoke-source-fetch-live.cmd`.
4. Verify TWSE live data on a machine/network where the TWSE SSL/TLS handshake succeeds.
5. Replace file-backed watchlist / account settings only when multi-user or multi-node persistence is required.

## Notes

- Do not add Docker, database, or React/Vite yet.
- Keep `/app` and `/api/v1` same-origin for local development.
- Keep mock fallback behavior for all external providers.
