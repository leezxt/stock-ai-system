# Stock AI System

股市分析前端 MVP + Spring Boot 後端骨架 + mock `/api/v1`。

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

主 `docker-compose.yml` 會一起啟動 Spring Boot app 與 PostgreSQL + pgvector。先把需要的 live key 放進 shell 環境變數，或直接在啟動命令前帶入：

```powershell
$env:OPENAI_API_KEY="your-key"
$env:ALPHAVANTAGE_API_KEY="your-key"
$env:FINMIND_API_TOKEN="your-token"
$env:FMP_API_KEY="your-key"
$env:STOCKAI_TWSE_DISCLOSURE_URL="https://your-json-endpoint.example/disclosures?symbol={symbol}&limit={limit}"
docker compose up --build
```

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
- `GET /api/v1/stocks/{market}/{symbol}/technical-summary`
- `GET /api/v1/stocks/{market}/{symbol}/prediction`
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
- `GET /api/v1/auth/me`
- `GET /api/v1/account/settings`
- `PUT /api/v1/account/settings`
- `GET /api/v1/watchlist`
- `POST /api/v1/watchlist`
- `DELETE /api/v1/watchlist/{market}/{symbol}`
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

### 自訂第三方 AI 供應商

登入後可在設定頁填入「自訂名稱／模型名稱」、「API Key」與完整的 OpenAI-compatible Chat Completions 請求網址，然後選擇自訂供應商執行分析或聊天。API Key 會以 AES-256-GCM 加密保存，不會回傳到前端。

安全限制：請求網址必須是公開的 `https://` 端點，只允許預設 443 埠，不接受 URL 內帳密、fragment、localhost 或私有／鏈路本地 IP。自訂名稱同時作為送給第三方 API 的 `model` 值。

### Windows 自架部署

專案可完全在自己的 Windows 主機執行，不需要外部雲端平台。先安裝並啟動 Docker Desktop，然後在專案根目錄執行：

```powershell
.\selfhost.ps1 start
```

第一次啟動會在被 Git 忽略的 `.env` 自動產生資料庫密碼、工作階段簽章密鑰與 API Key 加密密鑰。應用預設開放在主機的 `18080`，PostgreSQL 只綁定 `127.0.0.1`，不會直接暴露到區域網路或網際網路。

```text
http://localhost:18080/app
http://<主機區域網路 IP>:18080/app
```

管理指令：

```powershell
.\selfhost.ps1 status
.\selfhost.ps1 logs
.\selfhost.ps1 stop
```

若要讓網際網路使用者連線，應在應用前方配置 HTTPS reverse proxy 或安全 tunnel；不要直接對外開放 PostgreSQL 的 `5432`。

後端 AI key 解析順序：

1. Request header `X-OpenAI-Api-Key` / `X-Gemini-Api-Key` / `X-DeepSeek-Api-Key`
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

TWSE 上游或本機 TLS 失敗時，會自動退回 mock data。

台股新聞、月營收與財報資料可使用 FinMind。未設定 token 時會嘗試免 token 呼叫；若遇到額度或權限限制，請設定：

```powershell
$env:FINMIND_API_TOKEN="your-token"
$env:FINMIND_BASE_URL="https://api.finmindtrade.com/api/v4"
```

真實 AI 呼叫為 opt-in。OpenAI、Gemini 與 DeepSeek 可分別設定對應 API Key。先設定環境變數並重新啟動 backend：

```powershell
$env:OPENAI_API_KEY="your-key"
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
3. `scripts\stop-backend.cmd`
4. `scripts\start-spring-dashboard.cmd`
5. `scripts\smoke-openai-live.cmd`

`smoke-openai-live.cmd` 也會讀取 `local-env.cmd`，所以 key 放在同一個本機檔即可。若 backend 不是跑在 `http://localhost:8080/api/v1`，可另外設定 `STOCK_AI_BASE_URL`。

## 下一步

照 `docs/SPRING_BOOT_NEXT_STEPS.md`，優先補真實 AI provider 實作或 TW-capable market data provider；兩邊的 adapter seam 都已經在位。

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
- Google OAuth 自動帶入 provider 尚未完成，正式版目前以 API key 流程為主
- TWSE live runtime 在這台機器上會因 TLS handshake 問題回落 mock
- 台股公告預設走 MOPS 重大訊息；若 MOPS 不通可再設定自有 `STOCKAI_TWSE_DISCLOSURE_URL`
- PostgreSQL/pgvector 未設定時，watchlist、account settings 與 RAG chunks 仍會使用單機檔案 / 記憶體 fallback，適合單機開發
