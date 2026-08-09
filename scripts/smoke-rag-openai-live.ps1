$ErrorActionPreference = "Stop"

$embeddingKey = if ([string]::IsNullOrWhiteSpace($env:STOCKAI_RAG_EMBEDDING_API_KEY)) {
  $env:OPENAI_API_KEY
} else {
  $env:STOCKAI_RAG_EMBEDDING_API_KEY
}

if ([string]::IsNullOrWhiteSpace($embeddingKey)) {
  Write-Host "SKIP: no OpenAI RAG embedding key is configured. No live embedding request was made."
  exit 0
}

$embeddingKey = $embeddingKey.Trim()
if ($embeddingKey -eq "sk-your-real-key") {
  Write-Host "SKIP: OPENAI_API_KEY is still the example placeholder. No live embedding request was made."
  exit 0
}
if ($embeddingKey -match "\s" -or -not $embeddingKey.StartsWith("sk-")) {
  throw "OpenAI RAG embedding key is not a single-line platform key. Replace it in local-env.cmd without printing it to chat."
}

$baseUrl = if ([string]::IsNullOrWhiteSpace($env:STOCK_AI_BASE_URL)) { "http://localhost:8080/api/v1" } else { $env:STOCK_AI_BASE_URL.TrimEnd("/") }
$health = Invoke-RestMethod -Uri "$baseUrl/health" -TimeoutSec 5
$providers = $health.providers
if ($providers.ragEmbeddingProvider -ne "openai") {
  throw "RAG embedding provider is '$($providers.ragEmbeddingProvider)', expected openai. Set STOCKAI_RAG_EMBEDDING_PROVIDER=openai and restart backend."
}
if (-not $providers.ragEmbeddingConfigured) {
  throw "Backend health reports the OpenAI RAG embedding key is not configured. Restart backend after editing local-env.cmd."
}
if ($providers.ragEmbeddingDimension -ne 16) {
  throw "RAG embedding dimension is $($providers.ragEmbeddingDimension), expected 16."
}

$seed = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$authBody = @{ email = "rag-openai-smoke-$seed@example.test"; password = "Passw0rd!-rag-$seed" } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -ContentType "application/json" -Body $authBody -WebSession $session -TimeoutSec 15 | Out-Null

$publishedAt = (Get-Date).ToUniversalTime().ToString("o")
$importBody = @{
  symbol = "AAPL"
  market = "US"
  docType = "NEWS"
  title = "OpenAI semantic embedding smoke"
  source = "local-rag-smoke"
  publishedAt = $publishedAt
  content = "Apple services margin improved while iPhone demand supported the latest guidance outlook."
} | ConvertTo-Json

$imported = Invoke-RestMethod -Uri "$baseUrl/documents/import" -Method Post -ContentType "application/json" -Body $importBody -WebSession $session -TimeoutSec 45
if ($imported.data.chunksImported -lt 1) {
  throw "OpenAI RAG import returned no chunks."
}

$retrieveBody = @{
  queryText = "iPhone demand services margin guidance"
  topK = 3
  symbol = "AAPL"
  market = "US"
  docType = "NEWS"
} | ConvertTo-Json
$retrieved = Invoke-RestMethod -Uri "$baseUrl/documents/retrieve" -Method Post -ContentType "application/json" -Body $retrieveBody -WebSession $session -TimeoutSec 45
if ($null -eq $retrieved.data -or @($retrieved.data).Count -lt 1) {
  throw "OpenAI RAG retrieval returned no evidence."
}

Write-Host "OK: OpenAI RAG embedding smoke passed."
Write-Host "provider: $($providers.ragEmbeddingProvider)"
Write-Host "model: $($providers.ragEmbeddingModel)"
Write-Host "dimension: $($providers.ragEmbeddingDimension)"
Write-Host "chunks imported: $($imported.data.chunksImported)"
Write-Host "retrieved evidence: $(@($retrieved.data).Count)"
