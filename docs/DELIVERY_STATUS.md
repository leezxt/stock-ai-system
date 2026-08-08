# Stock AI MVP Delivery Status

## Current deliverable

This folder contains a runnable frontend MVP, zero-dependency mock API, and Spring Boot backend.

## Latest increment — 2026-08-09

- 預測新增不使用未來資料的 `local-logistic-v1` 本地監督式基線；歷史不足時才回退 `heuristic-momentum-v1`，並由資料品質標示可用性。
- Google OAuth 前端已接上 Google Identity Services，後端提供 `GET /api/v1/auth/google/config`；設定 `STOCKAI_GOOGLE_CLIENT_ID` 後才顯示登入按鈕，credential 仍由後端驗證。
- 新增 `scripts\smoke-all-live.cmd`，會自動找到健康 backend，聚合帳號、OpenAI、語意 RAG 與來源驗收，缺少外部條件時明確回報 `BLOCKED`。
- `/ai/chat` 新增可稽核的工具型問答路由與 `intent`／`answerStatus`／`tools[]`；工具結果顯示行情、技術、預測與文件來源，明顯非股票問題 fail-closed。
- 新增 `/stocks/{market}/{symbol}/data-lineage` 資料血緣端點；Yahoo 調整後收盤價與股利／分割事件會保留來源，價格圖不再混接未調整即時價，無事件時不自行推測除權息。
- 台股若同時取得 FinMind 歷史與 TWSE realtime，最新快照只更新摘要，`/prices` 與技術／預測序列維持 FinMind 單一歷史來源；血緣會標示 `SNAPSHOT_ONLY` 與 `CORPORATE_ACTION_FEED_NOT_AVAILABLE`。
- 自選股警示中心已加入帳號隔離與持久化規則，支援價格、漲跌幅、風險與資料品質條件；前端顯示觸發／停用／資料不可用狀態，並新增 `scripts\smoke-watchlist-alerts.cmd`。
- 警示歷史與去重已完成：V4 事件表／檔案 fallback 僅在狀態轉換時新增事件；重複刷新不重複計入 `newTriggerCount`，保留最近 20 筆本機歷史，沒有宣稱已發送外部通知。
- 警示排程已加入但預設關閉：啟用後逐帳號執行既有去重評估，`GET /api/v1/watchlist/alerts/scheduler` 提供安全的執行狀態與錯誤計數；通知模式固定 `LOCAL_ONLY`，未接外部通知服務。
- 本機通知佇列已完成：V5 只為新進入 `TRIGGERED` 的事件建立未讀通知，支援帳號隔離、已讀／未讀與規則刪除清理；未宣稱 Email／推播已送出。
- 通知偏好已完成：V6 保存帳號本機／Email／推播偏好意圖，但能力回應固定 `externalChannelsAvailable=false`；關閉本機通知只停止通知建立，不刪除警示事件。
- 已讀通知清理已完成：`DELETE /api/v1/watchlist/alerts/notifications/read` 只刪除目前帳號的本機 `READ` 通知，不影響警示規則與事件歷史。
- live smoke backend 探測已補強：除了 `/health`，`smoke-all-live.ps1` 現在驗證 API index 與來源抓取端點，避免舊版健康程序造成後續 404；目前本機仍因未啟用 live API key／加密 key 回報 `BLOCKED`。
- TWSE live 來源驗證已補齊：`smoke-twse-live.ps1` 直接比對官方 `STOCK_DAY_ALL` 與 backend 台股摘要／歷史序列；本機 2330 官方與 backend 最新價均為 2370，21 筆歷史均為 FinMind 來源且無 mock 混入。
- live 設定前置檢查已補齊：`check-live-config.ps1` 會安全辨識 key／加密 secret 缺少或仍為註解模板，不輸出秘密值，也不把 preflight 當成外部 AI 通過。
- 新增 `scripts\start-compose.cmd`：Windows Compose 啟動會自動載入 ignored `local-env.cmd`，OpenAI 語意 RAG 未設定 embedding key 時會在啟動前停止，避免以為已啟用語意模式但實際仍使用 `hash`。
- 真實模式 AI 失敗時不再顯示前端模擬模型分數或模擬偏多理由；模型比較改為 `unavailable-ai`／`--`，行情與技術資料仍獨立保留。
- AI 缺值與單點預測限制已補強：前端不再把缺少／越界的模型分數或偏多機率轉成 0；RAG 對不可用指標改用資料不足提示，prompt 以 `UNAVAILABLE` 隔離單點計算值。
- 真實行情載入修正缺值混用：價格 API 沒有有效序列時不再把本地 mock 歷史帶入圖表，最多保留有來源的即時單點；價格與日期採同筆解析，避免日期標籤錯位。
- 首頁 AI 共識修正全模型失敗情境：不再除以零顯示 `NaN`，改為 `--` 並標示未計算；模型比較也區分部分失敗與全部失敗。
- 策略回測的前端 mock／失敗回落不再製造總報酬、勝率、最大回撤或夏普比率；只顯示條件與資料限制，只有可驗證的歷史價格回放才呈現績效。
- 自選股清單修正模型分數平均：只計算成功模型，避免失敗模型造成 `NaN`；並新增 AI／行情來源、樣本數與資料品質，預測風險資料不足時顯示限制。
- 多模型比較頁新增共用資料品質提示，顯示技術／預測樣本數、來源、狀態與未可用指標，並標示 real 共用檢索上下文或 mock 未呼叫即時 provider。
- AI 問答的 mock／fallback 回覆已同步 `dataQuality`；MA20、RSI14、上漲機率與風險資料不足時不再顯示 fixture 數值，並補充技術／預測樣本數、來源與狀態。
- 個股分析新增「觀察重點」清單，呈現 backend `watchPoints` 的資料品質限制、MA20／MACD 追蹤條件與證據狀態；沒有真實分析時使用保守 fallback，不製造可用訊號。
- 個股分析新增獨立「消息面訊號」欄位，引用總覽已評估的可追溯新聞分數；未評估或新聞不足時不納入結論，並在切換個股時清除舊股票評分。
- 價格走勢圖已同步資料品質：均線圖例只顯示實際可用的線，並顯示繪製點數、來源與日期區間；真實 AAPL 24 筆資料只畫 MA5／MA20，MA60 不再出現在圖例。
- 個股分析 API 新增 `dataQuality`：回傳實際樣本數、日期範圍、行情來源、資料狀態與未可用指標；前端會把不足樣本的 MA／RSI／MACD／ATR 與單點預測顯示為資料不足，不再把少量資料當完整結果。
- RAG 分析 prompt 同步帶入技術／預測資料品質，要求 AI 揭露 `PARTIAL`、`SNAPSHOT_ONLY` 或模擬資料限制；API contract 與 OpenAPI 新增 `MarketDataQuality`、技術摘要與預測 response schema。
- 個股分析頁修正 AI 與技術模型數值混用：AI 分數、趨勢、上漲機率與 AI 風險統一來自同一份分析結果，結論另外列出技術模型風險。
- 策略回測已由固定 heuristic 結果改為 `historical-price-replay-v1` close-only 歷史收盤價回放，訊號模型標示為 `technical-momentum-replay-v1`，不把現在日期 AI 輸出回填到歷史。
- 回測新增持有天數、停利／停損、手續費、證交稅、滑價、資料點數、`COMPLETED`／`NO_SIGNALS`／`INSUFFICIENT_DATA` 狀態、逐筆交易明細與實際 `priceSources`；資料不足時不產生假交易。
- 前端策略回測已同步顯示完整條件、來源、資料狀態與交易明細；mock API 改以 `mock-backtest-v1` 明確標示 fixture，不再冒充歷史回放。
- 新增需登入的 `GET /api/v1/stocks/{market}/{symbol}/news-score?limit=5`，評估目前帳號可見的已匯入 `NEWS` 文件。
- 新聞評分保留逐篇來源、發布時間、RAG 相關度、命中詞、影響度與信心度；整體分數為 -100～100 的規則式新聞訊號，不代表價格預測。
- 沒有新聞時明確回傳 `INSUFFICIENT_DATA`，前端總覽新增「新聞評分評估」卡片，不會以模擬新聞產生分數。
- 新增 `scripts\\smoke-news-score.cmd`，會實際完成 auth、三篇 NEWS 匯入、評分結果與第二帳號 owner isolation 驗證。
- 最新驗證：`mvn -q test` 119 tests passed、`scripts\\test-all.cmd` passed、新聞評分 smoke passed、Compose host `18081` / PostgreSQL `15433` healthy、未授權新聞評分 API 回傳 401。
- Compose 真實行情驗證：AAPL 取得 24 筆 `yahoo-finance` 歷史資料時標示 `PARTIAL`，MA60／MACD 顯示資料不足；瀏覽器個股分析頁同步顯示樣本數、日期區間與來源。
- 回測驗證：`scripts\\smoke-backtest-conditions.cmd` 通過（`NO_SIGNALS`、29 data points）；瀏覽器以台股 2330.TW 完成真實歷史回放與逐筆交易明細驗證。

Security hardening completed on 2026-07-11: authenticated/limited cost-bearing APIs, HttpOnly session cookie, AES-GCM provider-key storage, owner-scoped RAG, HikariCP, Flyway migrations, dependency-aware health checks, same-origin frontend API defaults, and restricted CORS.

Start everything on Windows:

```powershell
scripts\start-spring-dashboard.cmd
```

Start Docker Compose on Windows with local provider settings:

```cmd
scripts\start-compose.cmd -d
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
- Provider-backed price charts preserve actual trading dates and per-point sources; realtime-only quotes remain one point instead of being expanded into synthetic history, and summary `priceSnapshot.updatedAt` uses provider observation time.
- Technical indicators
- Prediction cards
- Single-provider AI analysis
- AI analysis readability cleanup with summary cards and separated conclusion block
- Multi-model comparison
- Consensus score and divergence warning
- AI Chat
- Backtest
- Close-only historical price replay with configurable holding/cost/exit conditions, explicit data status, and trade-level details
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
- RAG embedding pipeline with explicit offline hash mode plus optional OpenAI `text-embedding-3-small` 16-dimensional mode; vector queries isolate embedding families and `DocumentEmbeddingService` keeps the same seam
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
- `scripts\check-no-secrets.cmd` working-tree/staged scan to keep local API keys out of GitHub sync
- `scripts\smoke-rag-openai-live.cmd` live-gated semantic RAG import/retrieve smoke path with safe skip for missing/example keys
- Account settings runtime smoke passed with a process-only encryption key; the smoke script now fails fast when encryption is not configured instead of producing a backend 500.
- Health and Settings now expose `secretsEncryptionConfigured` so account-key encryption readiness is visible before writes.
- Source-fetch live smoke now checks RAG embedding readiness before making external source requests.
- Source-fetch runtime smoke (hash override, temporary port 8081) imported TW financials 8 documents/22 chunks and TW announcements 2/2; Yahoo news/transcript returned 0 documents with warnings.
- Docker Compose build/runtime passed with PostgreSQL + pgvector on a fresh project (host 15433, app 18081); health/app and RAG import/retrieve checks passed. Existing database volumes were not deleted.
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
- TWSE parser test passes. The earlier local SSL/TLS fallback was rechecked on 2026-08-09; the official endpoint now responds and `smoke-twse-live.ps1` confirms the 2330 value against backend without mock history.
- `GET /api/v1/stocks/TW/2330/summary` returns `source: mock` while TWSE is unreachable, confirming fallback behavior.
- Latest Compose verification on 2026-08-08: `/api/v1/stocks/TW/2330.TW/prices?range=1M` returned 22 dated FinMind/TWSE points (`2026-07-08` through `2026-08-07`), summary `updatedAt` was the TWSE observation `2026-08-07T05:30:00Z`, and the in-app browser displayed `2026/8/7 下午1:30:00`.

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
4. Keep running `scripts\smoke-twse-live.cmd` after deployment changes; the current machine has completed one official TWSE／backend 2330 value comparison.
5. Replace file-backed watchlist / account settings with database storage only when multi-user or multi-node persistence is required.

Keep the frontend Base URL as:

```text
http://localhost:8080/api/v1
```

Skipped: React/Vite split. Add it when the UI needs multi-file maintenance or framework-level testing.

See `SPRING_BOOT_NEXT_STEPS.md` for the minimal backend build order.
See `../WORK_HANDOFF.md` for the latest continuation point.
