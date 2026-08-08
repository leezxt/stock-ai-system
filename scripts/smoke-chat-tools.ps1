$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$baseUrl = if ([string]::IsNullOrWhiteSpace($env:STOCK_AI_BASE_URL)) {
  "http://localhost:8080/api/v1"
} else {
  $env:STOCK_AI_BASE_URL.TrimEnd("/")
}

Invoke-RestMethod -Uri "$baseUrl/health" -TimeoutSec 5 | Out-Null
$seed = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$authBody = @{ email = "chat-tools-smoke-$seed@example.test"; password = "Passw0rd!-chat-tools-$seed" } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -ContentType "application/json" -Body $authBody -SessionVariable webSession -TimeoutSec 15 | Out-Null

$chatBody = @{
  market = "US"
  symbol = "AAPL"
  provider = "OPENAI"
  message = "Analyze future risk, technical indicators, prediction, and latest news for AAPL"
  history = @()
} | ConvertTo-Json -Depth 5
$chat = Invoke-RestMethod -Uri "$baseUrl/ai/chat" -Method Post -WebSession $webSession -ContentType "application/json" -Body $chatBody -TimeoutSec 30
$toolNames = @($chat.data.tools | ForEach-Object { $_.name })
foreach ($required in @("market_quote", "technical_indicators", "prediction_model")) {
  if ($toolNames -notcontains $required) {
    throw "chat tool result missing: $required"
  }
}
if (@("READY", "PARTIAL", "INSUFFICIENT_DATA") -notcontains [string]$chat.data.answerStatus) {
  throw "unexpected chat answerStatus: $($chat.data.answerStatus)"
}

$outOfScopeBody = @{ market = "US"; symbol = "AAPL"; provider = "OPENAI"; message = "What will the weather be tomorrow?" } | ConvertTo-Json
$outOfScope = Invoke-RestMethod -Uri "$baseUrl/ai/chat" -Method Post -WebSession $webSession -ContentType "application/json" -Body $outOfScopeBody -TimeoutSec 30
if ($outOfScope.data.source -ne "unavailable-ai:out-of-scope" -or $outOfScope.data.answerStatus -ne "OUT_OF_SCOPE") {
  throw "out-of-scope question did not fail closed"
}

Write-Host "OK: chat tools=$($toolNames -join ',') status=$($chat.data.answerStatus); out-of-scope=fail-closed"
