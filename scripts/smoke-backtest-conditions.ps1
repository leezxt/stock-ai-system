$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$baseUrl = if ([string]::IsNullOrWhiteSpace($env:STOCK_AI_BASE_URL)) {
  "http://localhost:8080/api/v1"
} else {
  $env:STOCK_AI_BASE_URL.TrimEnd("/")
}

Invoke-RestMethod -Uri "$baseUrl/health" -TimeoutSec 5 | Out-Null
$seed = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$authBody = @{ email = "backtest-smoke-$seed@example.test"; password = "Passw0rd!-backtest-$seed" } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -ContentType "application/json" -Body $authBody -SessionVariable webSession -TimeoutSec 15 | Out-Null

$request = @{
  market = "TW"
  symbol = "2330"
  strategy = @{
    minAiScore = 82
    minUpProbability = 0.68
    maxRiskLevel = "LOW"
    holdingDays = 7
    commissionRate = 0.001
    taxRate = 0.003
    slippageRate = 0.001
    takeProfitRate = 0.08
    stopLossRate = 0.04
  }
} | ConvertTo-Json -Depth 5

$response = Invoke-RestMethod -Uri "$baseUrl/backtests" -Method Post -WebSession $webSession -ContentType "application/json" -Body $request -TimeoutSec 20
$data = $response.data
if ($data.source -ne "historical-price-replay-v1") {
  throw "Unexpected backtest source: $($data.source)"
}
if ([int]$data.conditions.horizonDays -ne 7 -or [decimal]$data.conditions.minAiScore -ne 82 -or [decimal]$data.conditions.minUpProbability -ne 0.68 -or $data.conditions.maxRiskLevel -ne "LOW") {
  throw "Backtest conditions did not round-trip the requested thresholds."
}
if ($data.signalModel -ne "technical-momentum-replay-v1" -or [int]$data.dataPointCount -lt 0 -or -not [string]$data.status) {
  throw "Historical replay metadata is missing."
}
if (-not ($data.priceSources -is [System.Array]) -or $data.priceSources.Count -lt 1) {
  throw "Historical price source attribution is missing."
}
if ([string]::IsNullOrWhiteSpace($data.conditions.entryRule) -or [string]::IsNullOrWhiteSpace($data.conditions.exitRule) -or [string]::IsNullOrWhiteSpace($data.conditions.costModel) -or [string]::IsNullOrWhiteSpace($data.conditions.dataScope) -or -not ($data.conditions.exitRule -match "7") -or -not ($data.conditions.costModel -match "0.001")) {
  throw "Backtest conditions are missing detail fields."
}
if ($data.status -eq "COMPLETED" -and -not ($data.trades -is [System.Array])) {
  throw "Completed historical replay must return a trade list."
}

$invalidRequest = @{
  market = "TW"
  symbol = "2330"
  strategy = @{ minAiScore = 101; minUpProbability = 0.68; maxRiskLevel = "LOW" }
} | ConvertTo-Json -Depth 5
try {
  Invoke-RestMethod -Uri "$baseUrl/backtests" -Method Post -WebSession $webSession -ContentType "application/json" -Body $invalidRequest -TimeoutSec 20 | Out-Null
  throw "Invalid minAiScore should have returned HTTP 400."
} catch {
  $status = $_.Exception.Response.StatusCode.value__
  if ($status -ne 400) {
    throw "Invalid minAiScore returned HTTP $status instead of 400."
  }
}

Write-Host "OK: backtest conditions + validation smoke passed."
Write-Host "source: $($data.source)"
Write-Host "status: $($data.status), data points: $($data.dataPointCount)"
Write-Host "entry thresholds: echoed"
Write-Host "exit/cost/data-scope details: present"
