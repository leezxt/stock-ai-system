# Spring Boot Backend Next Steps

Goal: replace `mock-api-server.mjs` with a real Spring Boot backend while keeping the frontend Base URL unchanged:

```text
http://localhost:8080/api/v1
```

> This is the original phased plan. The later phases below are historical status notes; use `WORK_HANDOFF.md` for the current live-gate order.

## Phase 1: Runnable Shell

Create:

```text
backend/
├─ pom.xml
└─ src/main/java/com/example/stockai/
   ├─ StockAiApplication.java
   ├─ common/ApiResponse.java
   ├─ common/GlobalExceptionHandler.java
   ├─ market/Market.java
   ├─ market/SymbolNormalizer.java
   └─ health/HealthController.java
```

Minimum endpoint:

```http
GET /api/v1/health
```

Acceptance:

```powershell
mvn test
mvn spring-boot:run
```

Then open the frontend `Settings`, set Real API, and test `/health`.

Status: done. `backend` has the Spring Boot shell, `/api/v1/health`, `Market`, `SymbolNormalizer`, and tests.

## Phase 2: Mock Stock API

Implement the same mock data now in `mock-api-server.mjs`:

- `AAPL`
- `NVDA`
- `TSLA`
- `2330.TW`
- `2454.TW`
- `2317.TW`

Endpoints:

- `GET /api/v1/markets`
- `GET /api/v1/stocks/{market}/search`
- `GET /api/v1/stocks/{market}/{symbol}/summary`
- `GET /api/v1/stocks/{market}/{symbol}/prices`
- `GET /api/v1/stocks/{market}/{symbol}/technical-summary`
- `GET /api/v1/stocks/{market}/{symbol}/prediction`

Acceptance:

```powershell
scripts\test-all.cmd
```

Point `scripts\test-all.cmd` at the Spring backend or port-match Spring to `8080`.

Status: done in `backend`. Runtime verification covered health, markets, search, summary, prices, technical-summary, and prediction on temporary port `18085`.

## Phase 3: AI + Watchlist + Backtest

Implement:

- `POST /api/v1/ai/analysis`
- `POST /api/v1/ai/model-comparison`
- `POST /api/v1/ai/chat`
- `GET /api/v1/watchlist`
- `POST /api/v1/watchlist`
- `DELETE /api/v1/watchlist/{market}/{symbol}`
- `POST /api/v1/backtests`

Keep the first implementation mock-only. Do not call real AI providers yet.

Status: done in `backend`. Runtime verification covered AI analysis, model comparison, AI chat, backtest, and watchlist add/list/delete on temporary port `18086`.

## Phase 4: Replace Mock Internals

Only after the frontend works end-to-end:

1. `MarketDataProvider` seam is done. Add a real provider implementation and keep mock as fallback.
Status: partially done. `AlphaVantageMarketDataProvider` now supports opt-in US daily prices with mock fallback.

2. `AiProviderAdapter` seam is done. Add real provider implementations and keep mock as fallback.
Status: partially done. `OpenAiProviderAdapter` supports opt-in OpenAI Responses API calls with mock fallback. Runtime and browser verification covered no-key fallback.
3. Expand real market data coverage beyond the current US daily path, or add a TW-capable provider.
Status: partially done. `TwseMarketDataProvider` supports TWSE `STOCK_DAY_ALL` with mock fallback. Parser tests pass; `scripts\smoke-twse-live.cmd` now verifies the official endpoint and backend 2330 value on this machine, while fallback remains for deployment environments where TLS or the upstream is unavailable.
4. Replace mock prediction with ML service client plus fallback.
   Status: done for the local `local-logistic-v1` supervised baseline with a data-insufficient heuristic fallback; a remote model service remains optional.
5. Add timeouts and controlled provider errors where external provider calls are introduced.
6. Replace file-backed watchlist with database storage only when multi-user accounts are added.

## Do Not Do Yet

- JWT/auth
- Docker Compose
- real trading data providers
- real OpenAI/Claude/Gemini/DeepSeek calls
- database schema

Add these only after the `/api/v1` mock-compatible backend passes the current smoke tests.
