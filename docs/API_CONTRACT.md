# Stock AI MVP API Contract

Base URL:

```text
http://localhost:8080/api/v1
```

公開部署時前端使用同源 `/api/v1`。

## Authentication and limits

- 登入／註冊成功後由後端設定 `HttpOnly; SameSite=Lax` 的 `stockai_session` Cookie。
- CLI 仍可使用 `Authorization: Bearer <token>`；瀏覽器前端不讀取或保存 token。
- AI、backtest、watchlist、account settings、RAG import/retrieve 與 document source fetch 均要求登入。
- 模型比較最多 4 個 provider、chat 最多 2,000 字、RAG 文件最多 200,000 字、`topK` 最大 20。
- RAG 文件依登入 email 隔離；跨帳號不會互相檢索。

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
    "twseDisclosureUrl": ""
  }
}
```

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

## Prices

```http
GET /stocks/{market}/{symbol}/prices?range=1M
```

```json
{
  "data": {
    "prices": [
      { "date": "D-8", "close": 965 }
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
    "updatedAt": "2026-07-04T00:00:00.000Z"
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
    "modelVersion": "heuristic-momentum-v1",
    "generatedAt": "2026-07-04T00:00:00.000Z"
  }
}
```

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
  "providers": ["OPENAI", "CLAUDE", "GEMINI", "DEEPSEEK"],
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
  "provider": "CLAUDE",
  "message": "未來一週主要風險是什麼？"
}
```

```json
{
  "data": {
    "market": "US",
    "symbol": "AAPL",
    "provider": "CLAUDE",
    "source": "mock-ai",
    "message": "回覆內容。此回覆僅供研究，不代表投資建議。",
    "generatedAt": "2026-07-04T00:00:00.000Z"
  }
}
```

## Frontend Notes

- `summary.source` drives the Real mode market-source panel and Settings market-source status.
- `ai/analysis.source`, `ai/model-comparison.results[].source`, and `ai/chat.source` drive the AI-source panel and live/fallback labels.
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

## Backtest

```http
POST /backtests
```

```json
{
  "market": "TW",
  "symbol": "2330.TW",
  "strategy": {
    "minAiScore": 70,
    "minUpProbability": 0.6,
    "maxRiskLevel": "MEDIUM"
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
    "tradeCount": 42,
    "generatedAt": "2026-07-04T00:00:00.000Z"
  }
}
```
