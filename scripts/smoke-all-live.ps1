$ErrorActionPreference = "Stop"

function Find-BackendBaseUrl {
  $candidates = @()
  if (-not [string]::IsNullOrWhiteSpace($env:STOCK_AI_BASE_URL)) {
    $candidates += $env:STOCK_AI_BASE_URL.TrimEnd("/")
  }
  $candidates += @(
    "http://localhost:8080/api/v1",
    "http://localhost:18080/api/v1",
    "http://localhost:18081/api/v1"
  )
  foreach ($candidate in ($candidates | Select-Object -Unique)) {
    try {
      $health = Invoke-RestMethod -Uri "$candidate/health" -TimeoutSec 4
      if ($health.status -ne "UP") { continue }

      # A stale process can still report a healthy Spring context while it
      # does not expose the current API contract. Probe the unauthenticated
      # API index and require a route used by the current live smoke before
      # selecting the candidate.
      $index = Invoke-RestMethod -Uri "$candidate" -TimeoutSec 4
      $endpoints = @($index.endpoints | ForEach-Object { [string]$_ })
      if ($index.name -ne "Stock AI API" -or
          -not ($endpoints -contains "POST /api/v1/documents/source/news/fetch")) {
        continue
      }
      return $candidate
    } catch {}
  }
  return $null
}

function Invoke-Smoke($Label, $ScriptPath) {
  Write-Host "--- $Label ---"
  $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $ScriptPath 2>&1
  $exitCode = $LASTEXITCODE
  $output | ForEach-Object { Write-Host $_ }
  return $exitCode
}

$baseUrl = Find-BackendBaseUrl
if ([string]::IsNullOrWhiteSpace($baseUrl)) {
  Write-Host "BLOCKED: no healthy backend found on 8080, 18080, or 18081."
  exit 2
}
$env:STOCK_AI_BASE_URL = $baseUrl
Write-Host "Backend: $baseUrl"

$blocked = 0
$failed = 0
if ([string]::IsNullOrWhiteSpace($env:STOCKAI_SECRETS_ENCRYPTION_KEY) -and [string]::IsNullOrWhiteSpace($env:STOCKAI_AUTH_SECRET)) {
  Write-Host "BLOCKED: account-settings smoke needs STOCKAI_SECRETS_ENCRYPTION_KEY or STOCKAI_AUTH_SECRET."
  $blocked++
} else {
  $code = Invoke-Smoke "account settings" "scripts/smoke-account-settings.ps1"
  if ($code -ne 0) { $failed++ }
}

if ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)) {
  Write-Host "BLOCKED: OpenAI Responses smoke needs OPENAI_API_KEY."
  $blocked++
} else {
  $code = Invoke-Smoke "OpenAI Responses" "scripts/smoke-openai-live.ps1"
  if ($code -ne 0) { $failed++ }
}

$embeddingKey = if ([string]::IsNullOrWhiteSpace($env:STOCKAI_RAG_EMBEDDING_API_KEY)) { $env:OPENAI_API_KEY } else { $env:STOCKAI_RAG_EMBEDDING_API_KEY }
$ragProvider = ""
try { $ragProvider = [string](Invoke-RestMethod -Uri "$baseUrl/health" -TimeoutSec 5).providers.ragEmbeddingProvider } catch {}
if ([string]::IsNullOrWhiteSpace($embeddingKey) -or $ragProvider -ne "openai") {
  Write-Host "BLOCKED: semantic RAG smoke needs an OpenAI embedding key and backend provider=openai (current=$ragProvider)."
  $blocked++
} else {
  $code = Invoke-Smoke "OpenAI semantic RAG" "scripts/smoke-rag-openai-live.ps1"
  if ($code -ne 0) { $failed++ }
}

$code = Invoke-Smoke "TWSE official data" "scripts/smoke-twse-live.ps1"
if ($code -eq 2) { $blocked++ }
elseif ($code -ne 0) { $failed++ }

$code = Invoke-Smoke "market/document sources" "scripts/smoke-source-fetch-live.ps1"
if ($code -ne 0) { $failed++ }

Write-Host "Summary: failed=$failed blocked=$blocked backend=$baseUrl"
if ($failed -gt 0) { exit 1 }
if ($blocked -gt 0) { exit 2 }
exit 0
