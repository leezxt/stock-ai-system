# Stock AI MVP API Contract

Base URL:

```text
http://localhost:8080/api/v1
```

公開部署時前端使用同源 `/api/v1`。

## Authentication and limits

- 登入／註冊成功後由後端設定 `HttpOnly; SameSite=Lax` 的 `stockai_session` Cookie。
- `POST /auth/google` 接受 Google Identity Services credential；`GET /auth/google/config` 只回傳是否啟用與公開 client ID，client ID 由 `STOCKAI_GOOGLE_CLIENT_ID` 設定，credential 仍由 backend 向 Google tokeninfo 驗證。
- CLI 仍可使用 `Authorization: Bearer <token>`；瀏覽器前端不讀取或保存 token。
- AI、backtest、watchlist、account settings、RAG import/retrieve 與 document source fetch 均要求登入。
- 模型比較最多 4 個 provider、chat 最多 2,000 字、RAG 文件最多 200,000 字、`topK` 最大 20。
- RAG 文件依登入 email 隔離；跨帳號不會互相檢索。
- RAG 檢索會套用 `stockai.rag.relevance-threshold`（預設 `0.15`）；低於門檻的結果不會送入 AI context。
- RAG 目前以向量分數 75% + 文件關鍵詞重疊 25% 做 hybrid rerank；`stockai.rag.embedding.provider` 預設為 `hash`，可切換為 `openai` 使用 `text-embedding-3-small` 的 16 維投影。
- embedding provider 切換後必須重新匯入文件；向量查詢會隔離不同 `embeddingModel`，避免 hash 與語意向量混算。PostgreSQL 目前固定 `vector(16)`，因此 embedding dimension 只能維持 16。
- `openai` embedding 使用 backend 啟動時的 `STOCKAI_RAG_EMBEDDING_API_KEY`（未設定時回看 `OPENAI_API_KEY`）；這是伺服器級 RAG 設定，與聊天 endpoint 的帳號 key 分開。
- backend 會回傳 `X-Request-Id`；若呼叫端提供安全格式的同名 header，後端會沿用，否則自動產生 UUID，方便追蹤 AI fallback 與 latency。
- 前端載入個股時，行情歷史與 AI 分析分開處理；AI endpoint 未登入回 401 時仍保留已成功取得的歷史價格，不會退回搜尋結果的單點價格。

All responses may be either direct JSON or wrapped as:

```json
{ "data": {} }
```

The frontend currently accepts both.

## Health

```http
GET /health
```

Used by the frontend `Settings` tab to render provider readiness, next live action, and the OpenAI smoke-test hint.

```json
{
  "status": "UP",
  "timestamp": "2026-07-04T00:00:00.000Z",
  "providers": {
    "openAiConfigured": false,
    "openAiModel": "gpt-5.5",
    "alphaVantageConfigured": false,
    "alphaVantageNewsConfigured": false,
    "alphaVantageTranscriptConfigured": false,
    "finMindConfigured": true,
    "finMindNewsConfigured": true,
    "finMindFinancialsConfigured": true,
    "finMindTokenConfigured": false,
    "finMindBaseUrl": "https://api.finmindtrade.com/api/v4",
    "twseEndpointConfigured": true,
    "twseStockDayAllUrl": "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL",
    "twseDisclosureConfigured": false,
    "twseDisclosureUrl": "",
    "ragEmbeddingProvider": "hash",
    "ragEmbeddingModel": "hash-embedding-v1",
    "ragEmbeddingDimension": 16,
    "ragEmbeddingConfigured": true,
    "secretsEncryptionConfigured": false
  },
  "aiTelemetry": {
    "OPENAI.CHAT": {
      "requests": 0,
      "liveSuccesses": 0,
      "fallbacks": 0,
      "exceptions": 0,
      "totalLatencyMillis": 0
    }
  }
}
```

`aiTelemetry` is process-local and contains aggregate counters only; it does not include prompts, user identifiers, API keys, or provider response bodies. Counters reset when the backend restarts.

`health.providers.secretsEncryptionConfigured` indicates whether the backend has `STOCKAI_SECRETS_ENCRYPTION_KEY` (or the compatibility fallback `STOCKAI_AUTH_SECRET`). Account API keys are intentionally rejected until this is configured.

## Markets

```http
GET /markets
```

```json
{
  "data": ["US", "TW"]
}
```

## Stock Search

```http
GET /stocks/{market}/search?query=AAPL
```

```json
{
  "data": [
    {
      "symbol": "AAPL",
      "name": "Apple Inc.",
      "market": "US",
      "currency": "USD",
      "lastPrice": 217.63,
      "changePercent": 2.14
    }
  ]
}
```

## Stock Summary

```http
GET /stocks/{market}/{symbol}/summary
```

```json
{
  "data": {
    "symbol": "2330.TW",
    "name": "台積電",
    "market": "TW",
    "currency": "TWD",
    "timezone": "Asia/Taipei",
    "source": "mock",
    "priceSnapshot": {
      "symbol": "2330.TW",
      "market": "TW",
      "lastPrice": 1035,
      "changePercent": 1.47,
      "currency": "TWD",
      "updatedAt": "2026-07-04T00:00:00.000Z"
    }
  }
}
```

`priceSnapshot.updatedAt` is the provider observation time. For intraday TWSE
quotes it includes the exchange quote time; daily historical providers use the
latest trading date at the market timezone's start of day. Mock data has no
upstream observation and may fall back to the backend response time.

## Prices

```http
GET /stocks/{market}/{symbol}/prices?range=1M
```

```json
{
  "data": {
    "prices": [
      { "date": "2026-07-08", "close": 2465, "source": "finmind" }
    ]
  }
}
```

## Data Lineage

```http
GET /stocks/{market}/{symbol}/data-lineage
```

此端點回傳價格圖表實際使用的來源、觀測時間、交易日期區間、調整方式、完整性檢查結果與 provider 提供的公司行動。`corporateActions` 為空時不代表沒有除權息，只代表目前期間沒有事件或 provider 沒有事件 feed；系統不從價格缺口自行推測公司行動。`adjustmentStatus` 可能為 `ADJUSTED_CLOSE`、`UNADJUSTED_CLOSE`、`UNVERIFIED`、`SNAPSHOT_ONLY` 或 `MOCK`。`SNAPSHOT_ONLY` 表示最新報價只供摘要顯示，不能被當作歷史日線追加到圖表或指標序列。

```json
{
  "data": {
    "symbol": "AAPL",
    "market": "US",
    "source": "yahoo-finance",
    "adjustmentStatus": "ADJUSTED_CLOSE",
    "observedAt": "2026-07-04T00:00:00.000Z",
    "pricePointCount": 24,
    "dataFrom": "2026-06-10",
    "dataTo": "2026-07-04",
    "priceSources": ["yahoo-finance"],
    "integrityStatus": "PARTIAL",
    "findings": ["NO_ACTIONS_IN_WINDOW"],
    "corporateActions": [],
    "technicalQuality": {},
    "predictionQuality": {}
  }
}
```

## News Score

```http
GET /stocks/{market}/{symbol}/news-score?limit=5
```

此端點要求登入，只評估目前帳號可見且已匯入的 `NEWS` 文件；不會在沒有新聞時自行生成新聞或評分。評分為可追溯的規則式訊號，範圍為 `-100` 到 `100`，每篇文章會回傳來源、發布時間、RAG 相關度、命中詞與評估理由。`overallLabel` 可能是 `BULLISH`、`BEARISH`、`NEUTRAL` 或 `INSUFFICIENT_DATA`，不代表價格預測或投資建議。

```json
{
  "data": {
    "market": "TW",
    "symbol": "2330.TW",
    "articleCount": 3,
    "bullishCount": 1,
    "bearishCount": 1,
    "neutralCount": 1,
    "overallScore": 8,
    "overallLabel": "NEUTRAL",
    "confidence": 71,
    "latestPublishedAt": "2026-08-07T00:00:00Z",
    "evaluatedAt": "2026-08-08T00:00:00Z",
    "source": "rag-news-score-v1",
    "articles": [
      {
        "chunkId": "news-1",
        "title": "台積電上調展望",
        "source": "CNA",
        "publishedAt": "2026-08-07T00:00:00Z",
        "snippet": "需求強勁，營收成長並擴產。",
        "relevanceScore": 0.9,
        "sentimentScore": 84,
        "sentiment": "BULLISH",
        "impactScore": 88,
        "confidence": 91,
        "rationale": "偏多詞：上調、成長、擴產。此為規則式新聞訊號，不是價格預測。"
      }
    ]
  }
}
```

## Technical Summary

```http
GET /stocks/{market}/{symbol}/technical-summary
```

```json
{
  "data": {
    "symbol": "2330.TW",
    "market": "TW",
    "ma5": 1024.65,
    "ma20": 998.775,
    "ma60": 960.48,
    "rsi14": 62.5,
    "macdSignal": "BULLISH",
    "atr14": 26.91,
    "updatedAt": "2026-07-04T00:00:00.000Z",
    "dataQuality": {
      "sampleCount": 29,
      "requiredSampleCount": 60,
      "dataFrom": "2026-06-05",
      "dataTo": "2026-07-04",
      "source": "finmind",
      "status": "PARTIAL",
      "unavailableIndicators": ["MA60"]
    }
  }
}
```

## Prediction

```http
GET /stocks/{market}/{symbol}/prediction?horizonDays=5
```

```json
{
  "data": {
    "symbol": "AAPL",
    "market": "US",
    "horizonDays": 5,
    "upProbability": 0.6335,
    "expectedReturn": 0.01712,
    "volatility": 0.032,
    "riskLevel": "MEDIUM",
    "modelVersion": "local-logistic-v1",
    "generatedAt": "2026-07-04T00:00:00.000Z",
    "dataQuality": {
      "sampleCount": 29,
      "requiredSampleCount": 30,
      "dataFrom": "2026-06-05",
      "dataTo": "2026-07-04",
      "source": "finmind",
      "status": "OK",
      "unavailableIndicators": []
    }
  }
}
```

`dataQuality.status` 會是 `OK`、`PARTIAL`、`SNAPSHOT_ONLY`、`INSUFFICIENT_DATA` 或 `MOCK`。前端必須依 `unavailableIndicators` 將不足樣本的指標顯示為不可用；`sampleCount`、`dataFrom`、`dataTo` 與 `source` 用來追溯實際引用的行情資料，不代表 API 產生了完整歷史序列。

## AI Analysis

```http
POST /ai/analysis
```

```json
{
  "market": "TW",
  "symbol": "2330.TW",
  "provider": "OPENAI",
  "horizonDays": 5
}
```

```json
{
  "data": {
    "symbol": "2330.TW",
    "market": "TW",
    "provider": "OPENAI",
    "source": "mock-ai",
    "trend": "中性偏多",
    "aiScore": 73,
    "bullishProbability": 0.67,
    "riskLevel": "MEDIUM",
    "bullishReasons": ["短期均線維持支撐"],
    "bearishRisks": ["追價風險上升"],
    "watchPoints": ["MA20 支撐"],
    "conclusion": "模型輸出僅供研究，不代表投資建議。",
    "generatedAt": "2026-07-04T00:00:00.000Z"
  }
}
```

## Model Comparison

```http
POST /ai/model-comparison
```

```json
{
  "market": "US",
  "symbol": "NVDA",
  "providers": ["OPENAI", "GEMINI", "DEEPSEEK", "MIMO"],
  "horizonDays": 5
}
```

```json
{
  "data": {
    "symbol": "NVDA",
    "market": "US",
    "horizonDays": 5,
    "consensusScore": 70.5,
    "consensusTrend": "中性偏多",
    "averageBullishProbability": 0.65,
    "divergenceLevel": "LOW",
    "divergenceReason": "Mock providers 方向接近，風險評估略有差異。",
    "results": [
      {
        "provider": "OPENAI",
        "source": "mock-ai",
        "trend": "中性偏多",
        "aiScore": 68,
        "bullishProbability": 0.62,
        "riskLevel": "MEDIUM",
        "summary": "趨勢仍有支撐，需觀察量能延續。"
      }
    ],
    "generatedAt": "2026-07-04T00:00:00.000Z"
  }
}
```

## AI Chat

```http
POST /ai/chat
```

```json
{
  "market": "US",
  "symbol": "AAPL",
  "provider": "OPENAI",
  "message": "未來一週主要風險是什麼？",
  "history": [
    { "role": "user", "content": "先看一下目前趨勢" },
    { "role": "assistant", "content": "目前偏中性偏多，但仍需觀察 MA20。" }
  ]
}
```

`history` 可選，最多 8 輪、總長度最多 8,000 字；角色只接受 `user` 或 `assistant`。歷史內容與文件、目前問題一樣都屬不可信資料，只用於上下文，不會被當成系統指令。

`message` 是開放式股票研究問題，不使用快捷問題白名單；backend 會先以可稽核的工具路由器判斷意圖，查詢行情、技術指標、預測模型與帳號可見的新聞／財報文件，再交給 provider 回答。`answerStatus` 會是 `READY`、`PARTIAL`、`INSUFFICIENT_DATA` 或 `OUT_OF_SCOPE`；問題超出股票研究範圍時會 fail-closed，不呼叫 provider。若問題超出目前股票資料或文件證據，回覆必須指出資料限制，不得以固定範本代替問題。沒有可用 provider key 或 live provider 失敗時，`source` 會標示 `mock-ai:*`，此時回覆僅是降級提示，不代表生成式模型已回答。

```json
{
  "data": {
    "market": "US",
    "symbol": "AAPL",
    "provider": "OPENAI",
    "source": "mock-ai",
    "message": "回覆內容。此回覆僅供研究，不代表投資建議。",
    "evidence": [],
    "citationIds": [],
    "intent": "PREDICTION,RISK",
    "answerStatus": "READY",
    "tools": [
      {
        "name": "market_quote",
        "status": "READY",
        "summary": "最新價=200，漲跌=1.2%",
        "source": "yahoo-finance",
        "observedAt": "2026-07-04T00:00:00.000Z",
        "citationIds": []
      }
    ],
    "generatedAt": "2026-07-04T00:00:00.000Z"
  }
}
```

## Frontend Notes

- `summary.source` drives the Real mode market-source panel and Settings market-source status.
- `priceSnapshot.updatedAt` represents source observation time rather than the backend fetch time; this is the timestamp shown as `資料時間` in the frontend. Daily bars use the latest provider trading date at the market timezone, while mock data has no source observation.
- 每個價格點的 `date` 與 `source` 來自行情供應商；不再用工作日索引替代交易日期。只有 mock 資料才會產生示意日期；即時單點資料不會擴展成假歷史。
- `ai/analysis.source`, `ai/model-comparison.results[].source`, and `ai/chat.source` drive the AI-source panel and live/fallback labels.
- `ai/chat.intent`、`ai/chat.answerStatus` 與 `ai/chat.tools[]` 顯示後端實際路由與工具結果；`tools[].citationIds` 只會列出目前 `evidence` 中存在的 chunk ID。
- 前端 mock／fallback AI 問答會沿用目前股票 summary／technical／prediction 的 `dataQuality`；缺少樣本的指標或預測欄位必須顯示「資料不足」，不可把 mock fixture 數值當成可用分析。
- 多模型比較的分數與分歧必須搭配目前股票的技術／預測 `dataQuality` 解讀；real 模式共用同一份行情與檢索上下文，mock 模式則明確標示未呼叫即時 provider。
- 自選股清單的模型平均只計算成功結果；`source` 與 `dataQuality` 應和分數一起呈現，失敗或資料不足時使用 `--`／「資料不足」，不可把 fixture 當成即時行情或完整預測。
- 回測績效只有在 backend `historical-price-replay-v1` 以有日期收盤價完成回放時才可呈現；前端 mock 或 API 失敗回落不得從 AI 分數／機率推算績效，應顯示未執行歷史回放。
- 首頁 AI 共識與模型分歧只能使用成功且數值有效的模型結果；部分失敗要排除後重算，全部失敗時必須顯示未計算，不可回傳或渲染 `NaN`。
- 前端模型結果若缺少或超出範圍的 `aiScore`／`bullishProbability`，必須視為不可用，不得以 0 補值後納入共識或分析卡片。
- 真實模式若 AI analysis／model-comparison 呼叫失敗或沒有結果，前端不得回退到 mock 模型分數；必須顯示 `unavailable-ai`、`--` 與可操作的驗證提示，行情與技術資料可獨立保留。
- RAG prompt 對 `dataQuality.unavailableIndicators` 與 `INSUFFICIENT_DATA`／`SNAPSHOT_ONLY` 欄位必須使用 `UNAVAILABLE` 或資料不足限制，不得把相容性回傳的計算值當成可用證據。
- 真實行情圖表只可繪製 `/prices` 回傳的有效正價格點；若序列缺失，最多使用同一份 summary 的有來源單點快照，不可以本地 mock 價格補歷史。價格與日期必須逐點對齊。
- Taiwan `/stocks/{market}/{symbol}/prices` prefers a historical FinMind series; realtime-only fallback responses contain only the current quote and never append it to mock history. When both sources exist, `finmind+twse-realtime` is used only for the summary snapshot while the dated `/prices` series remains FinMind-only.
- `health.providers.openAiConfigured` and `health.providers.openAiModel` drive the OpenAI smoke-test hint and Settings readiness rows.
- `health.providers.alphaVantageNewsConfigured`, `finMindNewsConfigured`, `finMindFinancialsConfigured`, `alphaVantageTranscriptConfigured`, and `twseDisclosureConfigured` drive source-fetch readiness and live verification buttons.

## Watchlist

```http
GET /watchlist
```

```json
{
  "data": [
    {
      "symbol": "AAPL",
      "name": "Apple Inc.",
      "market": "US",
      "currency": "USD",
      "lastPrice": 217.63,
      "changePercent": 2.14
    }
  ]
}
```

```http
POST /watchlist
```

```json
{
  "market": "US",
  "symbol": "AAPL"
}
```

```json
{
  "data": {
    "symbol": "AAPL",
    "name": "Apple Inc.",
    "market": "US",
    "currency": "USD",
    "lastPrice": 217.63,
    "changePercent": 2.14
  }
}
```

```http
DELETE /watchlist/{market}/{symbol}
```

```json
{
  "data": {
    "market": "US",
    "symbol": "AAPL",
    "deleted": true
  }
}
```

## Watchlist Alerts

警示規則要求登入，且股票必須已在目前帳號的自選清單。規則不呼叫 AI，僅使用行情、預測資料品質與資料血緣結果；觸發只代表研究提醒，不是下單指令。

```http
GET /watchlist/alerts
```

```http
POST /watchlist/alerts
```

```json
{
  "market": "US",
  "symbol": "AAPL",
  "condition": "PRICE_ABOVE",
  "threshold": 200,
  "enabled": true
}
```

`condition` 支援 `PRICE_ABOVE`、`PRICE_BELOW`、`CHANGE_PCT_ABOVE`、`CHANGE_PCT_BELOW`、`RISK_AT_LEAST` 與 `DATA_QUALITY_NOT_OK`。價格門檻使用報價幣別；漲跌幅使用百分點；風險門檻使用 `LOW=1`、`MEDIUM=2`、`HIGH=3`；資料品質條件不需要 `threshold`。

```http
PUT /watchlist/alerts/{id}
DELETE /watchlist/alerts/{id}
```

回應的 `evaluations` 會列出 `TRIGGERED`、`NORMAL`、`DISABLED` 或 `DATA_UNAVAILABLE`，並附上 `source`、`dataStatus`、目前值與評估訊息。`newlyTriggered` 只有在規則由其他狀態轉入 `TRIGGERED` 時為 `true`，因此相同規則在重複刷新時不會重複計數；`newTriggerCount` 是本次新進入觸發的規則數。`recentEvents` 最多保留最近 20 筆狀態變更，僅供本機稽核與畫面顯示，未發送 Email、推播或交易指令。刪除規則時會一併刪除該規則的本機歷史。

```http
GET /watchlist/alerts/scheduler
```

排程狀態端點需要登入，只回傳是否啟用、評估間隔、最近完成時間、評估數量與錯誤計數，不回傳帳號清單。排程預設關閉；設定 `STOCKAI_WATCHLIST_ALERTS_SCHEDULER_ENABLED=true` 後，才會依 `STOCKAI_WATCHLIST_ALERTS_SCHEDULER_FIXED_DELAY_MS` 週期評估已註冊帳號。`notificationMode` 固定為 `LOCAL_ONLY`，目前只寫入既有警示歷史，不會發送外部通知。

```http
GET /watchlist/alerts/notifications?limit=20
PUT /watchlist/alerts/notifications/{id}
```

通知佇列只建立於規則首次進入 `TRIGGERED` 的狀態轉換，與事件去重共用 `eventId`，同一事件不會重複建立。回應包含目前帳號的 `UNREAD`／`READ` 通知與 `unreadCount`；`PUT` 的 body 為 `{ "read": true }` 或 `{ "read": false }`。刪除警示規則時會一併刪除其本機通知。

```http
GET /watchlist/alerts/notification-preferences
PUT /watchlist/alerts/notification-preferences
```

通知偏好以帳號隔離保存，`localEnabled` 預設為 `true`；`emailEnabled`／`pushEnabled` 目前只保存使用者意圖，回應的 `externalChannelsAvailable` 固定為 `false`，並附上阻擋原因。即使使用者勾選外部通道，系統仍只建立 `LOCAL_ONLY` 通知，不會外送。

```http
DELETE /watchlist/alerts/notifications/read
```

清理端點只刪除目前帳號的 `READ` 本機通知，回傳刪除數量；警示規則與 `recentEvents` 狀態轉換歷史會保留。

## Backtest

```http
POST /backtests
```

回測使用 `historical-price-replay-v1`，以 provider 提供的有日期收盤價逐根回放；訊號是 `technical-momentum-replay-v1` 技術動能代理，不是把現在日期的 AI 分析回填到歷史，也不使用未來資料。三個進場門檻以 AND 同時套用：訊號分數達標、上漲機率達標，且風險等級不高於 `maxRiskLevel`。可設定持有天數、停利、停損、買賣滑價、手續費與賣出證交稅；結果會回傳 `status`、資料點數、逐筆 `trades` 與可解釋的 `conditions`。收盤價資料不足時回傳 `INSUFFICIENT_DATA`，不產生假交易或假績效。

```json
{
  "market": "TW",
  "symbol": "2330.TW",
  "strategy": {
    "minAiScore": 70,
    "minUpProbability": 0.6,
    "maxRiskLevel": "MEDIUM",
    "holdingDays": 5,
    "commissionRate": 0.001425,
    "taxRate": 0.003,
    "slippageRate": 0.001,
    "takeProfitRate": 0.08,
    "stopLossRate": 0.04
  }
}
```

```json
{
  "data": {
    "symbol": "2330.TW",
    "market": "TW",
    "totalReturn": 0.12,
    "winRate": 0.56,
    "maxDrawdown": -0.098,
    "sharpeRatio": 1.17,
    "tradeCount": 3,
    "conditions": {
      "horizonDays": 5,
      "signalModel": "technical-momentum-replay-v1",
      "minAiScore": 70,
      "minUpProbability": 0.6,
      "maxRiskLevel": "MEDIUM",
      "entryRule": "technical signal score (proxy) >= 70 AND upProbability >= 0.6 AND risk <= MEDIUM",
      "exitRule": "固定持有 5 個交易日；停利=0.08、停損=0.04；未設定反向訊號出場。",
      "positionSizing": "單一標的、單次訊號名義部位；交易完成後以複利計算，不做資金再平衡。",
      "costModel": "單邊手續費率=0.001425、賣出稅率=0.003、單邊滑價率=0.001；未估計流動性衝擊。",
      "dataScope": "目前 provider 可用的有日期收盤價；使用技術動能代理訊號，不呼叫現在日期的 AI，也不使用未來資料。"
    },
    "status": "COMPLETED",
    "signalModel": "technical-momentum-replay-v1",
    "dataPointCount": 22,
    "note": "僅以有日期收盤價逐根回放。",
    "priceSources": ["finmind"],
    "trades": [
      {
        "entryDate": "2026-07-01",
        "exitDate": "2026-07-08",
        "entryPrice": 101.1,
        "exitPrice": 106.2,
        "grossReturn": 0.0504,
        "costRate": 0.0055,
        "netReturn": 0.0449,
        "win": true,
        "exitReason": "TAKE_PROFIT"
      }
    ],
    "source": "historical-price-replay-v1",
    "generatedAt": "2026-07-04T00:00:00.000Z"
  }
}
```
