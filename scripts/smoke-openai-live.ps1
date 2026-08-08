$ErrorActionPreference = "Stop"

function Get-OpenAiSmokeHint {
  param([string]$Source)
  if ($Source -like "*401-invalid_api_key*") {
    return "OpenAI rejected the key. Replace OPENAI_API_KEY in local-env.cmd with a valid OpenAI platform API key, then restart backend."
  }
  if ($Source -like "*429-insufficient_quota*") {
    return "The key is valid but the project/account has insufficient quota or billing is not enabled."
  }
  if ($Source -like "*404*model*") {
    return "The configured OPENAI_MODEL is not available to this key. Change OPENAI_MODEL to an available model and restart backend."
  }
  if ($Source -like "*openai-network-error*") {
    return "Backend could not reach OpenAI. Check network/proxy/firewall settings."
  }
  return "Check backend console and confirm backend was restarted after editing local-env.cmd."
}

if ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)) {
  Write-Host "SKIP: OPENAI_API_KEY is not set. No live OpenAI request was made."
  exit 0
}

$openAiKey = $env:OPENAI_API_KEY.Trim()
if ($openAiKey -eq "sk-your-real-key") {
  throw "OPENAI_API_KEY is still the example placeholder. Replace it in local-env.cmd with a real OpenAI platform API key; do not paste the key into chat."
}
if ($openAiKey -match "\s") {
  throw "OPENAI_API_KEY contains whitespace/newline characters. Please paste a single-line OpenAI API key into local-env.cmd."
}
if ($openAiKey.StartsWith("eyJ")) {
  throw "OPENAI_API_KEY looks like a JWT/login token, not an OpenAI API key. Paste an OpenAI platform API key that starts with sk-."
}
if (-not $openAiKey.StartsWith("sk-")) {
  Write-Host "WARN: OPENAI_API_KEY does not start with sk-. Continuing, but OpenAI may reject it."
}

$baseUrl = if ([string]::IsNullOrWhiteSpace($env:STOCK_AI_BASE_URL)) { "http://localhost:8080/api/v1" } else { $env:STOCK_AI_BASE_URL.TrimEnd("/") }

Invoke-RestMethod -Uri "$baseUrl/health" -TimeoutSec 5 | Out-Null

$seed = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$authBody = @{ email = "openai-smoke-$seed@example.test"; password = "Passw0rd!-smoke-$seed" } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -ContentType "application/json" -Body $authBody -SessionVariable webSession -TimeoutSec 15 | Out-Null

$chatBody = @{
  market = "US"
  symbol = "AAPL"
  provider = "OPENAI"
  message = "OpenAI smoke test. Reply with one short sentence."
} | ConvertTo-Json

$chat = Invoke-RestMethod -Uri "$baseUrl/ai/chat" -Method Post -WebSession $webSession -ContentType "application/json" -Body $chatBody -TimeoutSec 45
if ($chat.data.source -ne "openai-responses") {
  throw "OpenAI ai/chat live failed: $($chat.data.source). $((Get-OpenAiSmokeHint $chat.data.source))"
}

$analysisBody = @{
  market = "US"
  symbol = "AAPL"
  provider = "OPENAI"
  horizonDays = 5
} | ConvertTo-Json

$analysis = Invoke-RestMethod -Uri "$baseUrl/ai/analysis" -Method Post -WebSession $webSession -ContentType "application/json" -Body $analysisBody -TimeoutSec 45
if ($analysis.data.source -ne "openai-responses") {
  throw "OpenAI ai/analysis live failed: $($analysis.data.source). $((Get-OpenAiSmokeHint $analysis.data.source))"
}

Write-Host "OK: OpenAI live smoke passed."
Write-Host "chat source: $($chat.data.source)"
Write-Host "analysis score: $($analysis.data.aiScore)"
