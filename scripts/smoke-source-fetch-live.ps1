$ErrorActionPreference = "Stop"

$baseUrl = if ([string]::IsNullOrWhiteSpace($env:STOCK_AI_BASE_URL)) { "http://localhost:8080/api/v1" } else { $env:STOCK_AI_BASE_URL.TrimEnd("/") }

try {
  $health = Invoke-RestMethod -Uri "$baseUrl/health" -TimeoutSec 5
} catch {
  Write-Host "BLOCKED: backend is not reachable at $baseUrl"
  Write-Host "Start it first, then rerun this script."
  exit 1
}

$providers = $health.providers
$ragProvider = [string]$providers.ragEmbeddingProvider
if ($ragProvider -eq "openai" -and -not $providers.ragEmbeddingConfigured) {
  Write-Host "BLOCKED: RAG embedding provider is openai but no embedding key is configured. Set STOCKAI_RAG_EMBEDDING_API_KEY (or OPENAI_API_KEY), restart backend, then rerun."
  exit 1
}
$failed = $false
$seed = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$authBody = @{ email = "source-smoke-$seed@example.test"; password = "Passw0rd!-smoke-$seed" } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -ContentType "application/json" -Body $authBody -SessionVariable webSession -TimeoutSec 15 | Out-Null

function Invoke-SourceFetch($Label, $Path, $Body) {
  try {
    $json = $Body | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$baseUrl$Path" -Method Post -WebSession $webSession -ContentType "application/json" -Body $json -TimeoutSec 45
    $count = [int]$response.data.documentCount
    $chunks = [int]$response.data.chunkCount
    $adapter = $response.data.adapter
    if ($count -gt 0) {
      Write-Host "OK: $Label adapter=$adapter documents=$count chunks=$chunks"
    } else {
      Write-Host "WARN: $Label adapter=$adapter returned 0 documents / 0 chunks"
    }
    return $true
  } catch {
    Write-Host "FAIL: $Label fetch failed: $($_.Exception.Message)"
    return $false
  }
}

if ($providers.alphaVantageNewsConfigured -or $providers.fmpNewsConfigured -or $providers.yahooFinanceNewsConfigured) {
  if (-not (Invoke-SourceFetch "US news" "/documents/source/news/fetch" @{
    market = "US"
    symbol = "AAPL"
    limit = 2
  })) { $failed = $true }
} else {
  Write-Host "SKIP: US news has no AlphaVantage key, FMP key, or Yahoo RSS URL configured."
}

if ($providers.alphaVantageTranscriptConfigured -or $providers.fmpTranscriptConfigured -or $providers.yahooFinanceNewsConfigured) {
  if (-not (Invoke-SourceFetch "US transcript" "/documents/source/transcripts/fetch" @{
    market = "US"
    symbol = "AAPL"
    quarter = "2026Q2"
  })) { $failed = $true }
} else {
  Write-Host "SKIP: US transcript has no AlphaVantage key, FMP key, or Yahoo fallback configured."
}

if ($providers.finMindNewsConfigured -or $providers.yahooFinanceNewsConfigured) {
  if (-not (Invoke-SourceFetch "TW news" "/documents/source/news/fetch" @{
    market = "TW"
    symbol = "2330"
    limit = 2
  })) { $failed = $true }
} else {
  Write-Host "SKIP: TW news has no FinMind or Yahoo fallback configured."
}

if ($providers.finMindFinancialsConfigured -or $providers.yahooFinanceFinancialsConfigured) {
  if (-not (Invoke-SourceFetch "TW financials" "/documents/source/financials/fetch" @{
    market = "TW"
    symbol = "2330"
    limit = 2
  })) { $failed = $true }
} else {
  Write-Host "SKIP: financials has no FinMind or Yahoo source configured."
}

if ($providers.twseDisclosureConfigured -or $providers.yahooFinanceNewsConfigured) {
  if (-not (Invoke-SourceFetch "TW announcement" "/documents/source/announcements/fetch" @{
    market = "TW"
    symbol = "2330"
    limit = 2
  })) { $failed = $true }
} else {
  Write-Host "SKIP: TW announcement has no TWSE disclosure URL or Yahoo fallback configured."
}

if ($failed) {
  exit 1
}
