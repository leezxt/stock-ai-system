# Stock AI System

一個以台股、美股研究為核心的 AI 股市分析工作台，提供行情查詢、價格走勢、技術指標、趨勢預測、模型比較、AI 問答、RAG 文件檢索、自選股與回測功能。專案採「真實來源優先、明確標示 fallback」的設計：每筆行情保留供應商日期與來源，資料不足時不會把單點報價偽裝成歷史序列。

> 本專案定位為個人研究與分析工具，不提供券商下單，也不構成投資建議。AI、行情與回測結果都必須確認資料來源與時間後再解讀。

## 專案簡介

### 核心能力

- **行情與圖表**：支援台股 FinMind、TWSE realtime/OpenAPI，以及美股 Yahoo Finance、Alpha Vantage；圖表保留實際交易日與逐點來源，摘要的 `updatedAt` 代表來源觀測時間。
- **資料品質與血緣**：`data-lineage` 會顯示實際價格序列、調整方式、完整性檢查、缺漏原因與 provider 公司行動；沒有事件資料時不從價格缺口猜測除權息。
- **技術分析**：提供 MA5/MA20/MA60、RSI14、MACD、ATR14、波動度與區間統計。
- **AI 分析與問答**：支援 OpenAI、Gemini、DeepSeek、MIMO；AI 問答採開放式問題、有限多輪上下文，先由 backend 工具路由器查詢行情／技術／預測／新聞／財報，再回傳意圖、資料狀態、工具來源與可驗證 citation。超出股票研究範圍時 fail-closed，回覆會標示 live 或 `mock-ai` fallback 狀態。預測預設使用可重現的 `local-logistic-v1` 基線模型，資料不足時才退回舊 heuristic 並標示來源。
- **策略回測**：可設定訊號分數（技術代理）、上漲機率、最高風險、持有天數、停利停損與交易成本；以 `historical-price-replay-v1` 逐根回放有日期收盤價，回傳實際 `priceSources`、資料狀態、逐筆交易與條件限制。資料不足時不產生假績效。
- **新聞評分評估**：針對已匯入的新聞計算 -100～100 多空訊號、影響度、信心度與整體分布，逐篇保留來源、發布時間、RAG 相關度與命中詞理由；沒有新聞時回傳資料不足。
- **RAG 證據鏈**：文件匯入、向量檢索、hybrid rerank、文件來源與 citation ID 驗證；可使用離線 `hash` embedding，也可切換 OpenAI `text-embedding-3-small` 16 維語意模式。
- **使用者功能**：HttpOnly session、帳號設定、自選股、回測，以及 PostgreSQL + pgvector 持久化。
- **可驗證部署**：Spring Boot API 與靜態前端可本機啟動，也可用 Docker Compose 一起啟動 PostgreSQL 與應用程式。

### 技術架構

```text
Browser dashboard (frontend/index.html)
        │ same-origin /api/v1
        ▼
Spring Boot 4.1 + Java 21
  ├─ Stock / technical / prediction services
  ├─ AI provider adapters + fallback labeling
  ├─ RAG ingestion / embedding / retrieval
  ├─ Auth / account settings / watchlist
  └─ Health / request-id / rate-limit controls
        │
        └─ PostgreSQL + pgvector (Docker Compose)
```

### 資料與安全邊界

- 預設可離線以 mock 行情、`mock-ai` 與 `hash` embedding 啟動；真實 provider 必須另外設定對應環境變數。
- API key 只透過環境變數、被忽略的 `local-env.cmd` 或後端加密帳號設定提供，不應寫入 source、README、資料庫明文、log 或 prompt。
- RAG 文件依登入帳號隔離；切換 embedding provider 後必須重新匯入文件，避免混用不同向量模型。
- 行情來源、AI 來源與 fallback 狀態會在 API 和前端顯示，方便追蹤資料是否真的來自外部服務。

## 專案沿革

| 時間 | 階段 | 主要成果 |
|---|---|---|
| 2026-07-06～2026-07-10 | MVP 與資料服務骨架 | 建立 Spring Boot API、靜態分析工作台、台股/美股查詢、技術指標、預測、回測與 mock API；同步建立 API contract 與 RAG 開發路線。 |
| 2026-07-06～2026-07-10 | RAG 與來源匯入 | 完成文件匯入、切 chunk、embedding、向量檢索、證據回傳，以及新聞、財報、公告、法說逐字稿等來源 adapter。 |
| 2026-08-08 | 歷史價格回測 | 回測改為 close-only historical replay，加入技術動能代理訊號、持有天數、停利停損、手續費／證交稅／滑價、資料不足狀態與逐筆交易明細；不把目前 AI 分數回填到歷史。 |
| 2026-07-10 | 帳號與持久化 | 加入登入、帳號設定、自選股、加密 provider key、使用者隔離、SHA-256 檔案路徑與 atomic write。 |
| 2026-07-11 | 安全與部署強化 | 加入 HttpOnly session、API 認證與限流、RAG owner scope、Flyway/HikariCP、PostgreSQL 健康檢查、CORS 限制與 request correlation。 |
| 2026-08-07 | AI 問答升級 | 從固定快捷問題改為開放式問答，加入有限多輪上下文、citation ID、證據門檻、hybrid rerank、AI telemetry 與 OpenAI 語意 embedding 選項。 |
| 2026-08-08 | 行情正確性與 Compose 驗證 | 修正價格圖表的交易日期、逐點來源與 realtime-only fallback；`updatedAt` 改為來源觀測時間，並完成 Docker Compose、PostgreSQL/pgvector、API 與瀏覽器驗證。 |
| 2026-08-08 | 新聞評分評估 | 新增登入保護的新聞評分 API、逐篇來源與理由、資料不足狀態、前端評估卡片，以及帳號隔離的端到端 smoke test。 |
| 2026-08-08 | 本地 ML 與登入驗證補齊 | 加入不使用未來資料的 `local-logistic-v1` 預測基線、Google Identity Services 可選登入 UI、Google client 設定查詢，以及單一 live smoke 聚合入口。 |
| 2026-08-08 | AI 工具型問答 | `/ai/chat` 新增價格、技術指標、預測、新聞／財報證據與回測引導工具；回傳 `intent`、`answerStatus`、`tools`，非股票問題不呼叫 provider。 |
| 2026-08-08 | 資料品質與公司行動 | 新增資料血緣端點與 smoke test；Yahoo 除權息事件保留來源，台股即時快照不再混入歷史日線，價格圖僅標記 provider 明確回傳的股利／分割，不從價格缺口推測。 |
| 2026-08-08 | 自選股警示中心 | 新增帳號隔離、可持久化的價格／漲跌幅／風險／資料品質警示規則；前端顯示觸發結果、來源與資料狀態，不會產生下單指令。 |
| 2026-08-08 | 警示歷史與去重 | 以 V4 事件表／使用者檔案保存狀態轉換；`newlyTriggered` 與 `newTriggerCount` 只計入由其他狀態轉入觸發，重複刷新不重複計數，保留最近 20 筆本機歷史。 |
| 2026-08-08 | 警示排程與通知準備 | 新增預設關閉的全域排程評估、登入後可查詢的排程狀態與錯誤計數；啟用後沿用警示事件去重，通知模式固定為 `LOCAL_ONLY`，不呼叫 Email／推播。 |
| 2026-08-09 | 本機通知佇列 | 新觸發事件會建立帳號隔離的 `UNREAD` 通知，可透過 API／前端標記已讀；刪除規則同步刪除通知，仍不對外發送。 |
| 2026-08-09 | 通知偏好與 fail-closed | 新增帳號隔離的本機／Email／推播偏好保存與能力狀態；外部通道即使被勾選仍明確阻擋，維持 `LOCAL_ONLY`。 |
| 2026-08-09 | 通知清理 | 新增只清除目前帳號 `READ` 通知的維運端點與前端操作，不影響警示規則或事件稽核歷史。 |
| 2026-08-09 | Live smoke 版本探測 | live 驗收會檢查 API index 與來源端點，不再把舊程序僅因 `/health` 為 UP 就誤選成目前 backend。 |
| 2026-08-09 | TWSE live 來源驗證 | 新增官方 `STOCK_DAY_ALL`／backend 摘要與歷史序列比對 smoke；本機 2330 官方與 backend 最新價均為 2370，歷史序列維持 FinMind 單一來源。 |
| 2026-08-09 | Live 設定前置檢查 | 新增不呼叫外部 AI 的設定診斷，能指出 key／加密 secret 缺少或仍是 `local-env.cmd` 註解模板。 |

目前專案已可在本機以 mock 或設定後的真實 provider 執行；正式 live key、外部來源額度與生產環境驗收仍需依部署環境個別確認。

## 結構

```text
stock-ai-system/
├─ backend/      Spring Boot 4.1.0 + Java 21 backend
├─ frontend/     Static dashboard MVP
├─ tools/        Mock API server and smoke test
├─ docs/         API contract, OpenAPI, delivery notes
└─ scripts/      Windows helper scripts
```

## 一鍵啟動前端與 Spring Boot backend

```powershell
scripts\start-spring-dashboard.cmd
```

## Docker Compose 啟動

主 `compose.yaml` 會一起啟動 Spring Boot app 與 PostgreSQL + pgvector。先把需要的 live key 放進 shell 環境變數，或直接在啟動命令前帶入：

```powershell
$env:OPENAI_API_KEY="your-key"
$env:STOCKAI_RAG_EMBEDDING_PROVIDER="openai" # optional; hash is the safe default
$env:STOCKAI_RAG_EMBEDDING_API_KEY=$env:OPENAI_API_KEY
# STOCKAI_RAG_EMBEDDING_API_KEY is required when provider=openai
$env:STOCKAI_SECRETS_ENCRYPTION_KEY="local-compose-encryption-secret-32-bytes"
$env:STOCKAI_GOOGLE_CLIENT_ID="your-client-id.apps.googleusercontent.com" # optional; public client ID
$env:MIMO_API_KEY="your-key"
$env:ALPHAVANTAGE_API_KEY="your-key"
$env:FINMIND_API_TOKEN="your-token"
$env:FMP_API_KEY="your-key"
$env:STOCKAI_TWSE_DISCLOSURE_URL="https://your-json-endpoint.example/disclosures?symbol={symbol}&limit={limit}"
docker compose up --build
```

Windows 也可以使用啟動入口，讓被 `.gitignore` 排除的 `local-env.cmd` 自動載入；它不會印出 key，且選擇 OpenAI 語意 RAG 卻沒有 embedding key 時會在啟動前停止：

```cmd
scripts\start-compose.cmd -d
```

這個入口的 RAG 預設仍是離線 `hash`。若 `local-env.cmd` 設定 `STOCKAI_RAG_EMBEDDING_PROVIDER=openai`，同時設定 `STOCKAI_RAG_EMBEDDING_API_KEY`（或 `OPENAI_API_KEY`）後才會啟動語意模式；切換 provider 後必須重新匯入文件。

若要一次檢查目前環境所有 live 依賴，可執行：

```cmd
scripts\check-live-config.cmd
scripts\smoke-all-live.cmd
```

前者只檢查設定是否啟用、是否仍是註解模板、加密 secret 長度與 backend API contract，不會呼叫外部 AI；`smoke-all-live.cmd` 也會自動先跑這個 preflight，再執行帳號、OpenAI、語意 RAG、TWSE 與來源 smoke。缺少 API key 或加密設定時標示 `BLOCKED`，不會把未驗收狀態誤報為通過。

若主機的 5432 已被占用，可只改 host mapping，不需改容器內 PostgreSQL 連線：

```powershell
$env:STOCK_AI_POSTGRES_PORT="15432"
docker compose up -d --build
```

Compose volume 會保留 PostgreSQL 密碼；若已有舊 volume，`STOCKAI_DATABASE_PASSWORD` 必須與建立該 volume 時相同。不要為了繞過密碼錯誤直接執行 `docker compose down -v`，除非確認資料可刪除。

Compose 會把 key 只注入容器環境，不會寫入 `compose.yaml`。`STOCKAI_RAG_EMBEDDING_PROVIDER` 預設為 `hash`；切換成 `openai` 後，需重新匯入文件。`STOCKAI_SECRETS_ENCRYPTION_KEY` 用於帳號 API key 的 AES-GCM 儲存，正式環境請改用外部 secret 管理。

啟動後開：

```text
http://localhost:18080/app
```

Docker app 會自動使用容器內 PostgreSQL：

```text
jdbc:postgresql://postgres:5432/stock_ai
```

停止：

```powershell
docker compose down
```

若要連同持久化資料 volume 一起刪除：

```powershell
docker compose down -v
```

## PostgreSQL + pgvector 持久化

未設定資料庫時，backend 仍會使用既有的檔案 / 記憶體 fallback，方便單機開發。若只想單獨啟動 PostgreSQL + pgvector 給本機 backend 使用，可執行：

```powershell
docker compose -f docker-compose.postgres.yml up -d
```

接著在 `local-env.cmd` 加入：

```cmd
set "STOCKAI_DATABASE_URL=jdbc:postgresql://localhost:5432/stock_ai"
set "STOCKAI_DATABASE_USERNAME=stock_ai"
set "STOCKAI_DATABASE_PASSWORD=stock_ai"
```

重啟 backend 後，Spring Boot 會自動建立：

- `stockai_users`
- `stockai_account_settings`
- `stockai_watchlist`
- `stockai_stock_snapshots`
- `stockai_document_chunks`，其中 `embedding` 使用 `pgvector vector(16)`

RAG embedding 預設使用離線 `hash` 模式；要切換 OpenAI 語意 embedding，請在 `local-env.cmd` 設定後重啟 backend：

```cmd
set "STOCKAI_RAG_EMBEDDING_PROVIDER=openai"
set "STOCKAI_RAG_EMBEDDING_API_KEY=%OPENAI_API_KEY%"
set "OPENAI_EMBEDDING_MODEL=text-embedding-3-small"
```

目前資料庫固定 16 維，切換 provider 後要重新匯入文件；系統會隔離不同 `embeddingModel`，不會混用舊 hash 向量。

停止資料庫：

```powershell
docker compose -f docker-compose.postgres.yml down
```

若要連資料庫 volume 一起清掉：

```powershell
docker compose -f docker-compose.postgres.yml down -v
```

若你本機的 8080 已經被其他 backend 佔用，可改 host port：

```powershell
$env:STOCK_AI_HOST_PORT="18081"
docker compose up --build
```

這會啟動 Spring Boot backend，並開啟同源前端：

```text
http://localhost:8080/app
```

停止 backend：

```powershell
scripts\stop-backend.cmd
```

目前腳本行為：

- `scripts\start-spring-dashboard.cmd` 若偵測到 `http://localhost:8080/api/v1/health` 已可用，會直接開 `http://localhost:8080/app`，不再重複開第二個 backend。
- `scripts\stop-backend.cmd` 若 8080 沒有 listener，會直接回報 `No backend listening on 8080.`。
- `scripts\start-backend.cmd` 會先顯示 backend 工作目錄與目前載入的本機設定，再執行 `mvn spring-boot:run`。

## 一鍵啟動前端與 mock API

```powershell
scripts\start-dashboard.cmd
```

頁面開啟後，在 `Settings`：

- Mode 選 `Real API`
- Base URL 使用 `http://localhost:8080/api/v1`
- 按 `測試 /health`

## 測試 mock API

```powershell
scripts\test-all.cmd
```

## 測試 Spring Boot backend

```powershell
$env:JAVA_HOME='C:\Users\lee\.jdks\ms-21.0.10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd backend
mvn test
```

## 目前後端已完成

- `GET /api/v1/health`
- `GET /api/v1/markets`
- `GET /api/v1/stocks/{market}/search`
- `GET /api/v1/stocks/{market}/{symbol}/summary`
- `GET /api/v1/stocks/{market}/{symbol}/prices`
- `GET /api/v1/stocks/{market}/{symbol}/data-lineage`
- `GET /api/v1/stocks/{market}/{symbol}/technical-summary`
- `GET /api/v1/stocks/{market}/{symbol}/prediction`
- `GET /api/v1/stocks/{market}/{symbol}/news-score?limit=5`（需登入）
- `POST /api/v1/ai/analysis`
- `POST /api/v1/ai/model-comparison`
- `POST /api/v1/ai/chat`
- `POST /api/v1/documents/import`
- `POST /api/v1/documents/retrieve`
- `POST /api/v1/documents/source/news/fetch`
- `POST /api/v1/documents/source/announcements/fetch`
- `POST /api/v1/documents/source/transcripts/fetch`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/google`
- `GET /api/v1/auth/google/config`
- `GET /api/v1/auth/me`
- `GET /api/v1/account/settings`
- `PUT /api/v1/account/settings`
- `GET /api/v1/watchlist`
- `POST /api/v1/watchlist`
- `DELETE /api/v1/watchlist/{market}/{symbol}`
- `GET /api/v1/watchlist/alerts`
- `POST /api/v1/watchlist/alerts`
- `PUT /api/v1/watchlist/alerts/{id}`
- `DELETE /api/v1/watchlist/alerts/{id}`
- `GET /api/v1/watchlist/alerts/scheduler`
- `GET /api/v1/watchlist/alerts/notifications`
- `PUT /api/v1/watchlist/alerts/notifications/{id}`
- `GET /api/v1/watchlist/alerts/notification-preferences`
- `PUT /api/v1/watchlist/alerts/notification-preferences`
- `DELETE /api/v1/watchlist/alerts/notifications/read`
- `POST /api/v1/backtests`

啟動 Spring Boot backend:

```powershell
scripts\start-backend.cmd
```

若只想開頁面並沿用現有 backend，優先用：

```powershell
scripts\start-spring-dashboard.cmd
```

後端 base URL 會回端點清單：

```text
http://localhost:8080/api/v1
```

前端頁面：

```text
http://localhost:8080/app
```

帳號系統目前提供最小可用版本機登入：

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/google`
- `GET /api/v1/auth/google/config`
- `GET /api/v1/auth/me`
- `GET /api/v1/account/settings`
- `PUT /api/v1/account/settings`

AI provider key 的行為分兩種：

- 未登入：Key 只保留在目前頁面的記憶體，重新整理即清除，不寫入 `localStorage`
- 已登入：前端改走 `/api/v1/account/settings`；Key 以 AES-256-GCM 加密後存入 PostgreSQL 或本機 fallback 檔案

登入使用 HttpOnly、SameSite Cookie；瀏覽器 JavaScript 不會取得實際 session token。AI、回測、watchlist、RAG 匯入／檢索與來源抓取端點均要求登入，並套用每使用者與 IP 的分鐘限流。

正式環境必須設定：

```text
STOCKAI_AUTH_SECRET=<至少 32 字元的隨機值>
STOCKAI_SECRETS_ENCRYPTION_KEY=<另一組至少 32 字元的隨機值>
STOCKAI_CORS_ALLOWED_ORIGINS=https://your-domain.example
```

若未另外設定 `STOCKAI_SECRETS_ENCRYPTION_KEY`，系統會以 `STOCKAI_AUTH_SECRET` 經領域分離雜湊後作為相容 fallback；正式環境仍建議使用獨立密鑰。

後端 AI key 解析順序：

1. Request header `X-OpenAI-Api-Key` / `X-Gemini-Api-Key` / `X-DeepSeek-Api-Key` / `X-Mimo-Api-Key`
2. 已登入帳號的後端儲存 key
3. backend 環境變數 / JVM 設定

Watchlist 未設定 PostgreSQL 時會使用單機檔案持久化，已支援 guest 與登入帳號分流。預設檔案在 `backend\data\watchlist.txt` 與 `backend\data\watchlist-*.txt`。需要改 guest 主檔位置時可加 JVM 參數：

```powershell
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dstockai.watchlist.file=C:\path\watchlist.txt"
```

真實 US 日線資料目前可選接 Alpha Vantage。美股新聞與法說會逐字稿另支援 FMP 作為 Alpha Vantage 的替代來源。設定需要的 key 即可：

```powershell
$env:ALPHAVANTAGE_API_KEY="your-key"
$env:FMP_API_KEY="your-key"
```

```powershell
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dstockai.alpha-vantage.api-key=your-key"
```

未設定 key、TW symbol、或上游失敗時，會自動退回 mock data。

另外，美股目前已內建免 key 的 Yahoo Finance US provider，會優先提供：

- `US` 個股名稱查詢
- `US` summary / search 價格資料

`/api/v1/health` 會顯示：

- `yahooFinanceUsConfigured`
- `yahooFinanceUsChartUrl`

真實台股日收盤資料目前可選接 TWSE OpenAPI：

```text
https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL
```

可覆蓋 endpoint：

```powershell
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dstockai.twse.stock-day-all-url=https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL"
```

TWSE 上游或本機 TLS 失敗時，會自動退回 mock data；可用 `scripts\smoke-twse-live.cmd` 檢查官方值、backend 摘要與歷史序列是否一致。

台股新聞、月營收與財報資料可使用 FinMind。未設定 token 時會嘗試免 token 呼叫；若遇到額度或權限限制，請設定：

```powershell
$env:FINMIND_API_TOKEN="your-token"
$env:FINMIND_BASE_URL="https://api.finmindtrade.com/api/v4"
```

真實 AI 呼叫為 opt-in。OpenAI 可設定 `OPENAI_API_KEY`，MIMO 可設定 `MIMO_API_KEY`；MIMO 預設使用 OpenAI-compatible Chat Completions endpoint 與 `mimo-v2.5-pro` 模型。先設定環境變數並重新啟動 backend：

```powershell
$env:OPENAI_API_KEY="your-key"
$env:MIMO_API_KEY="your-key"
$env:MIMO_MODEL="mimo-v2.5-pro"
scripts\start-spring-dashboard.cmd
```

也可以複製本機設定檔，避免每次手動設定：

```powershell
copy local-env.example.cmd local-env.cmd
notepad local-env.cmd
scripts\stop-backend.cmd
scripts\start-spring-dashboard.cmd
```

`local-env.cmd` 已被 `.gitignore` 排除，不要提交真實 key。

可選模型：

```powershell
$env:OPENAI_MODEL="gpt-5.5"
```

live smoke test：

```powershell
scripts\smoke-openai-live.cmd
```

也可以直接在 `/app` 的 `Settings -> API 狀態` 內按：

- `刷新 live 狀態`
- `驗證 OpenAI live`
- `驗證來源抓取`

前端會把結果寫回頁面狀態列，缺 key / URL 時會直接顯示 skip 原因。

Auth + account settings smoke test：

```powershell
scripts\smoke-account-settings.cmd
```

來源抓取 smoke test：

```powershell
scripts\smoke-source-fetch-live.cmd
```

歷史價格回測條件與資料狀態 smoke test（會建立暫時帳號，驗證門檻、持有天數、成本欄位、歷史回放來源與非法門檻拒絕）：

```powershell
scripts\smoke-backtest-conditions.cmd
```

新聞評分端到端 smoke test（會建立暫時帳號、匯入三篇測試新聞，並驗證多空分布與帳號隔離）：

```powershell
scripts\smoke-news-score.cmd
```

AI 工具型問答 smoke test（會驗證行情／技術／預測工具結果，以及非股票問題 fail-closed）：

```powershell
scripts\smoke-chat-tools.cmd
```

自選股警示 smoke test（會驗證登入、排程狀態、通知偏好 fail-closed、首次觸發、本機通知、已讀、清除已讀、重複刷新去重、停用與刪除）：

```powershell
scripts\smoke-watchlist-alerts.cmd
```

資料血緣與價格調整狀態 smoke test（會驗證來源、調整狀態、完整性與公司行動欄位）：

```powershell
scripts\smoke-data-lineage.cmd
```

TWSE 官方來源與台股 2330 最新摘要／歷史序列 live smoke：

```powershell
scripts\smoke-twse-live.cmd
```

這支腳本會檢查：

- Alpha Vantage 或 FMP 美股新聞抓取
- FinMind 台股新聞抓取
- FinMind 台股財報抓取
- Alpha Vantage 或 FMP 法說會逐字稿抓取
- 台股 MOPS 重大訊息公告抓取

其中美股新聞/法說會可在 `local-env.cmd` 設定：

```powershell
set "ALPHAVANTAGE_API_KEY=your-key"
set "FMP_API_KEY=your-key"
```

台股公告預設使用 MOPS 公開資訊觀測站重大訊息 URL；若要替換成自有公告 JSON 端點，才需要設定 `STOCKAI_TWSE_DISCLOSURE_URL`。

目前 `local-env.cmd` 已在專案根目錄建立，可直接編輯本機 key 與 URL。

最短順序：

1. `copy local-env.example.cmd local-env.cmd`
2. 在 `local-env.cmd` 設定 `OPENAI_API_KEY`
   - 若要執行帳號設定 smoke，另需設定 `STOCKAI_SECRETS_ENCRYPTION_KEY`（或 `STOCKAI_AUTH_SECRET`）
3. `scripts\stop-backend.cmd`
4. `scripts\start-spring-dashboard.cmd`
5. `scripts\smoke-openai-live.cmd`
6. 若要驗證語意 RAG，執行 `scripts\smoke-rag-openai-live.cmd`；它會匯入一份本機 smoke 文件並確認 OpenAI embedding 能完成檢索。

`smoke-openai-live.cmd` 也會讀取 `local-env.cmd`，所以 key 放在同一個本機檔即可。若 backend 不是跑在 `http://localhost:8080/api/v1`，可另外設定 `STOCK_AI_BASE_URL`。

### API key 與 GitHub 同步

- `local-env.cmd` 已列入 `.gitignore`，本機驗證可以使用真實 API key，但不會隨程式碼同步到 GitHub。
- 同步前先執行 `scripts\check-no-secrets.cmd -Staged`；它會檢查 staged 檔案與常見 API key / secret 變數，發現金鑰就中止。
- `scripts\test-all.cmd` 也會先執行工作區 secret scan。若金鑰曾經誤提交，應立即撤銷該 key 並清理 Git 歷史，不能只刪除目前檔案。

## 下一步

照 `WORK_HANDOFF.md` 的 live gate 順序，優先完成真實 OpenAI／語意 RAG 金鑰驗收與外部資料來源驗證；本機預測基線、Google 登入 UI 與持久化 adapter 已在位。

RAG 第二階段規劃見：

```text
docs/RAG_PHASE_PLAN.md
```

## MVP 交付結論

第一版 MVP 已完成交付檢查。

目前可交付內容：

- `/app` 前端工作台
- `/api/v1` Spring Boot backend
- mock fallback + opt-in live provider readiness
- 最小可用帳號登入與帳號設定 API
- 已登入使用者的後端 AI key 儲存
- user-scoped watchlist / account settings 檔案路徑已改為 SHA-256 命名，並保留舊檔相容讀取
- watchlist / AI analysis / chat / backtest / model comparison
- API contract / OpenAPI / handoff / RAG phase plan

目前已知限制：

- OpenAI live call 仍需本機設定 `OPENAI_API_KEY` 後重啟 backend
- Google OAuth 已有可選登入流程；仍需設定 `STOCKAI_GOOGLE_CLIENT_ID` 並完成 Google Console 的來源網域／網路驗收
- TWSE live runtime 本次在這台機器通過官方 `STOCK_DAY_ALL`／backend 2330 比對；部署環境仍應執行 `scripts\smoke-twse-live.cmd`，失敗時才回落 mock
- 台股公告預設走 MOPS 重大訊息；若 MOPS 不通可再設定自有 `STOCKAI_TWSE_DISCLOSURE_URL`
- PostgreSQL/pgvector 未設定時，watchlist、account settings 與 RAG chunks 仍會使用單機檔案 / 記憶體 fallback，適合單機開發
