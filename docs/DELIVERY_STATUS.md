# Stock AI MVP Delivery Status

## Current deliverable

This folder contains a runnable frontend MVP, zero-dependency mock API, and Spring Boot backend.

Security hardening completed on 2026-07-11: authenticated/limited cost-bearing APIs, HttpOnly session cookie, AES-GCM provider-key storage, owner-scoped RAG, HikariCP, Flyway migrations, dependency-aware health checks, same-origin frontend API defaults, and restricted CORS.

Start everything on Windows:

```powershell
scripts\start-spring-dashboard.cmd
```

Run verification:

```powershell
scripts\test-all.cmd
```

## Done

- Spring Boot backend Phase 1 shell
- `GET /api/v1` endpoint index in backend
- `GET /api/v1/health` in backend
- Spring Boot stock endpoints
- Spring Boot AI, model comparison, chat, backtest, and watchlist mock endpoints
- `MarketDataProvider` seam for future real market data integration
- Optional Alpha Vantage US daily provider with mock fallback
- Optional TWSE OpenAPI daily close provider with mock fallback
- `AiProviderAdapter` seam for future real AI provider integration
- Frontend Real mode source panel for market and AI source states
- Frontend Settings live-readiness rows with status pills for market source, AI source, provider keys, and next action
- Settings hint for OpenAI live smoke prerequisites and exact next command
- Backend `/health` provider readiness for OpenAI, Alpha Vantage, and TWSE config
- API contract sync for `source` fields and `/health.providers` frontend dependencies
- Local startup flow cleanup for backend reuse and clearer stop-backend reporting
- Frontend wording aligned with mock, live provider, and fallback source states
- Spring Boot CORS config for `/api/**`
- File-backed watchlist persistence
- Java 21 Maven test path verified
- Same-origin frontend route at `GET /app`
- Dark fintech dashboard UI
- US / TW market switch
- Stock search and symbol normalization
- Price snapshot and mock price chart
- Technical indicators
- Prediction cards
- Single-provider AI analysis
- AI analysis readability cleanup with summary cards and separated conclusion block
- Multi-model comparison
- Consensus score and divergence warning
- AI Chat
- Backtest
- Backtest status cleanup with rule summary, source state, and fallback messaging
- Watchlist add/remove
- Search and watchlist UX cleanup for Enter-to-search, duplicate add feedback, and current real-loaded symbol rows
- Mock / Real API mode
- URL parameter startup for Real API mode
- Compact loading states for Real API stock load and AI Chat
- Ignored `local-env.cmd` support for local API keys in backend start and OpenAI smoke scripts
- `/api/v1` mock server
- API contract document
- OpenAPI 3.0 draft
- Smoke test covering all implemented endpoints
- Phase 2 RAG plan document for document-grounded analysis and shared-context model comparison
- RAG backend document metadata schema with validated `DocumentChunk` and `DocumentType`
- RAG document ingestion pipeline for import, clean, and chunk
- RAG vector store contract with in-memory implementation as the pgvector handoff layer
- RAG embedding pipeline with deterministic hash embedding and `DocumentEmbeddingService`
- RAG retriever with filterable request object and evidence-style result rows
- RAG context assembly layer that combines technical summary, prediction output, and retrieved evidence for analysis, model comparison, chat, and backtest
- RAG document import and retrieval API for indexing chunks into the current in-memory vector store
- Frontend document import panel in `Settings` for manual RAG seeding without calling the API by hand
- Frontend evidence display for analysis and chat based on `POST /api/v1/documents/retrieve`
- Analysis and chat responses now embed `evidence`, reducing frontend split-call dependency
- Evidence UI polish for citation-style cards, explicit empty states, and shared-context visibility in model comparison
- Source adapters for US news, US earnings-call transcripts, and configurable TW disclosure JSON ingestion
- Frontend source-fetch controls for news, TW financials, announcements, and transcripts in the Settings panel
- Source-fetch readiness surfaced in `/health` and frontend Settings, including TW news and financials
- Root `local-env.cmd` scaffold plus `scripts\smoke-source-fetch-live.cmd` for live source-fetch verification
- Auth MVP with register/login/me endpoints
- Account settings API for logged-in provider preference and server-side OpenAI/Gemini key storage
- Frontend logged-in settings flow that writes AI keys to backend account settings instead of browser localStorage
- `scripts\smoke-account-settings.cmd` for auth + account settings runtime verification
- SHA-256 user-scoped persistence paths for watchlist and account settings with legacy filename fallback
- Atomic file writes for users, watchlists, and account settings

## Verified

Backend:

- `backend`: `mvn test` with JDK 21 passed, 42 tests
- Spring Boot `/api/v1` endpoint index verified on temporary port `18087`
- Spring Boot runtime API check passed on temporary port `18086`
- Verified backend endpoints: health, markets, search, summary, prices, technical-summary, prediction, AI analysis, model comparison, AI chat, backtest, watchlist add/list/delete
- In-app browser verified `http://localhost:8080/app`, Real mode AAPL load, `OPENAI / mock-ai` source display, and AI Chat `[mock-ai]` response.
- Frontend syntax check passed after adding the Real mode source panel.
- Frontend syntax check passed after adding compact loading states.
- Frontend syntax check passed after search/watchlist flow cleanup.
- Frontend syntax check passed after AI analysis readability cleanup.
- Frontend syntax check passed after adding Settings provider status pills.
- Frontend syntax check passed after backtest status cleanup.
- Frontend syntax check passed after OpenAI live smoke hint update.
- OpenAPI JSON parse passed after contract sync update.
- `scripts\stop-backend.cmd` verified the no-listener path and now reports `No backend listening on 8080.`
- `scripts\smoke-openai-live.cmd` verified the safe no-key skip path and verified ignored `local-env.cmd` loading with a temporary dummy key. Live OpenAI call still requires `OPENAI_API_KEY` and backend restart.
- `docs/RAG_PHASE_PLAN.md` added and linked from README plus handoff for post-MVP work.
- Full smoke test rerun on 2026-07-06 passed for `scripts\test-all.cmd`, `mvn test`, frontend script parse, and `scripts\stop-backend.cmd`.
- MVP delivery check completed on 2026-07-06.
- RAG step 56 backend schema check passed on 2026-07-06 with `mvn test` total 17 tests.
- RAG step 57 ingestion check passed on 2026-07-06 with `mvn test` total 19 tests.
- RAG step 58 vector store check passed on 2026-07-06 with `mvn test` total 21 tests.
- RAG step 59 embedding check passed on 2026-07-06 with `mvn test` total 23 tests.
- RAG step 60 retriever check passed on 2026-07-06 with `mvn test` total 24 tests.
- RAG step 61 context assembly check passed on 2026-07-06 with `mvn test` total 26 tests.
- RAG step 62 import API check passed on 2026-07-06 with `mvn test` total 27 tests.
- RAG step 63 frontend import panel check passed on 2026-07-06 with frontend script parse.
- RAG step 64 frontend evidence rendering check passed on 2026-07-06 with frontend script parse.
- RAG step 65 response-embedded evidence check passed on 2026-07-06 with `mvn test` total 28 tests and frontend script parse.
- RAG step 66 evidence UI polish check passed on 2026-07-06 with frontend script parse.
- RAG step 67 source-adapter import check passed on 2026-07-06 with `mvn test` total 32 tests.
- RAG step 68 frontend source-fetch panel check passed on 2026-07-06 with frontend script parse.
- RAG step 69 source-readiness health check passed on 2026-07-06 with `mvn test` total 32 tests and frontend script parse.
- RAG step 70 prep added local env scaffold and source-fetch smoke script for US news, TW news, TW financials, transcripts, and TW announcements; current live verification remains blocked until backend is started and real keys/URL are configured.
- Account settings step added `/api/v1/account/settings`, backend file persistence under `data/account-settings`, request-header -> account-settings -> env key resolution, and frontend logged-in save/load integration. `mvn test` passed with 39 tests and frontend script parse passed.
- Persistence hardening step added `common.UserScopedFileLocator`, migrated new user-scoped persistence filenames to SHA-256, preserved legacy filename load compatibility, and added migration tests for watchlist plus account settings. `mvn test` passed with 41 tests and `scripts\smoke-account-settings.ps1` passed.
- Atomic write step added `common.AtomicFileWriter`, switched file-backed writes to temp-file replacement, and added `UserStoreTest`. `mvn test` passed with 42 tests and `scripts\smoke-account-settings.ps1` passed.
- TWSE parser test passes. Local runtime currently falls back to mock because direct access to `https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL` fails at SSL/TLS handshake on this machine.
- `GET /api/v1/stocks/TW/2330/summary` returns `source: mock` while TWSE is unreachable, confirming fallback behavior.

`scripts\test-all.cmd` currently covers the Node mock API:

- `GET /api/v1/health`
- `GET /api/v1/stocks/{market}/{symbol}/summary`
- `GET /api/v1/stocks/{market}/{symbol}/prices`
- `GET /api/v1/stocks/{market}/{symbol}/prediction`
- `POST /api/v1/ai/analysis`
- `POST /api/v1/ai/model-comparison`
- `POST /api/v1/ai/chat`
- `POST /api/v1/backtests`
- `GET /api/v1/watchlist`
- `POST /api/v1/watchlist`
- `DELETE /api/v1/watchlist/{market}/{symbol}`

## Next practical step

Replace backend mock internals with real services in small steps:

1. Start backend and run `scripts\smoke-account-settings.cmd` to verify auth + account settings end to end.
2. Copy `local-env.example.cmd` to `local-env.cmd`, set `OPENAI_API_KEY`, restart backend, then run the OpenAI live-call smoke path.
3. Verify source-fetch live adapters with `ALPHAVANTAGE_API_KEY` and optional `STOCKAI_TWSE_DISCLOSURE_URL`.
4. Verify TWSE live data on a machine/network where the TWSE SSL/TLS handshake succeeds.
5. Replace file-backed watchlist / account settings with database storage only when multi-user or multi-node persistence is required.

Keep the frontend Base URL as:

```text
http://localhost:8080/api/v1
```

Skipped: React/Vite split. Add it when the UI needs multi-file maintenance or framework-level testing.

See `SPRING_BOOT_NEXT_STEPS.md` for the minimal backend build order.
See `../WORK_HANDOFF.md` for the latest continuation point.
