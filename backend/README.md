# Stock AI Backend

Spring Boot backend for the Stock AI MVP.

## Requirements

- JDK 21
- Maven

On this machine, use:

```powershell
$env:JAVA_HOME='C:\Users\lee\.jdks\ms-21.0.10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## Test

```powershell
mvn test
```

## Run

```powershell
mvn spring-boot:run
```

## Docker Compose

從專案根目錄啟動：

```powershell
docker compose up --build
```

若需要 live provider：

```powershell
$env:OPENAI_API_KEY="your-key"
$env:MIMO_API_KEY="your-key"
$env:MIMO_MODEL="mimo-v2.5-pro"
$env:ALPHAVANTAGE_API_KEY="your-key"
$env:FINMIND_API_TOKEN="your-token"
$env:FMP_API_KEY="your-key"
$env:STOCKAI_TWSE_DISCLOSURE_URL="https://your-json-endpoint.example/disclosures?symbol={symbol}&limit={limit}"
docker compose up --build
```

`FMP_API_KEY` 可作為 Alpha Vantage 美股新聞與法說會逐字稿的替代來源。台股公告預設使用 MOPS 重大訊息 URL，只有要改成自有 JSON 端點時才需要設定 `STOCKAI_TWSE_DISCLOSURE_URL`。

Health endpoint:

```text
http://localhost:8080/api/v1/health
```

Frontend:

```text
http://localhost:8080/app
```

## Current Scope

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
- `GET /api/v1/watchlist`
- `POST /api/v1/watchlist`
- `DELETE /api/v1/watchlist/{market}/{symbol}`
- `POST /api/v1/backtests`
- `Market` enum
- `SymbolNormalizer`
- mock US/TW stock data
- mock AI, watchlist, and backtest data
- file-backed watchlist persistence
- `/app` frontend route
- CORS for `/api/**`
- minimal error response

Watchlist default file:

```text
backend/data/watchlist.txt
```

Override:

```powershell
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dstockai.watchlist.file=C:\path\watchlist.txt"
```

Next: replace mock internals with real data providers and AI provider adapters.
