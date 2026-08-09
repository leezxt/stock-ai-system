$ErrorActionPreference = "Stop"

function Has-Value([string]$name) {
  return -not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))
}

function Has-CommentedTemplate([string]$name) {
  if (-not (Test-Path -LiteralPath "local-env.cmd")) { return $false }
  $escaped = [regex]::Escape($name)
  $pattern = '^\s*rem\s+set\s+"' + $escaped + '='
  return $null -ne (Select-String -LiteralPath "local-env.cmd" -Pattern $pattern -Quiet)
}

function Write-Missing([string]$name, [string]$hint) {
  if (Has-CommentedTemplate $name) {
    Write-Host "BLOCKED: $name is not active; local-env.cmd still has its commented template. $hint"
  } else {
    Write-Host "BLOCKED: $name is missing. $hint"
  }
}

$blocked = 0
$provider = if (Has-Value "STOCKAI_RAG_EMBEDDING_PROVIDER") {
  $env:STOCKAI_RAG_EMBEDDING_PROVIDER.Trim().ToLowerInvariant()
} else {
  "hash"
}

if (-not (Has-Value "OPENAI_API_KEY")) {
  Write-Missing "OPENAI_API_KEY" "Set it only in local-env.cmd or the process environment; do not paste it into chat."
  $blocked++
} else {
  Write-Host "OK: OPENAI_API_KEY is present (value hidden)."
}

if ($provider -notin @("hash", "openai")) {
  Write-Host "BLOCKED: STOCKAI_RAG_EMBEDDING_PROVIDER must be hash or openai (current=$provider)."
  $blocked++
} elseif ($provider -eq "openai") {
  if (-not (Has-Value "STOCKAI_RAG_EMBEDDING_API_KEY") -and -not (Has-Value "OPENAI_API_KEY")) {
    Write-Missing "STOCKAI_RAG_EMBEDDING_API_KEY" "Set it or set OPENAI_API_KEY, then restart Compose."
    $blocked++
  } else {
    Write-Host "OK: semantic RAG provider=openai and embedding key is present (value hidden)."
  }
} else {
  Write-Host "INFO: semantic RAG provider=hash (offline mode; no OpenAI embedding call will be made)."
}

if (-not (Has-Value "STOCKAI_SECRETS_ENCRYPTION_KEY") -and -not (Has-Value "STOCKAI_AUTH_SECRET")) {
  Write-Missing "STOCKAI_SECRETS_ENCRYPTION_KEY" "Set it (or STOCKAI_AUTH_SECRET) before account-settings live smoke."
  $blocked++
} else {
  $secretName = if (Has-Value "STOCKAI_SECRETS_ENCRYPTION_KEY") { "STOCKAI_SECRETS_ENCRYPTION_KEY" } else { "STOCKAI_AUTH_SECRET" }
  $secretValue = [Environment]::GetEnvironmentVariable($secretName)
  if ($secretValue.Trim().Length -lt 16) {
    Write-Host "BLOCKED: $secretName must contain at least 16 characters (value hidden)."
    $blocked++
  } else {
    Write-Host "OK: account-settings encryption secret is present and long enough (value hidden)."
  }
}

$candidates = @()
if (Has-Value "STOCK_AI_BASE_URL") { $candidates += $env:STOCK_AI_BASE_URL.TrimEnd("/") }
$candidates += @(
  "http://localhost:18081/api/v1",
  "http://localhost:18080/api/v1",
  "http://localhost:8080/api/v1"
)
$backend = $null
foreach ($candidate in ($candidates | Select-Object -Unique)) {
  try {
    $health = Invoke-RestMethod -Uri "$candidate/health" -TimeoutSec 4
    $index = Invoke-RestMethod -Uri "$candidate" -TimeoutSec 4
    $endpoints = @($index.endpoints | ForEach-Object { [string]$_ })
    if ($health.status -eq "UP" -and $index.name -eq "Stock AI API" -and
        ($endpoints -contains "POST /api/v1/documents/source/news/fetch")) {
      $backend = $candidate
      break
    }
  } catch {}
}
if ($null -eq $backend) {
  Write-Host "BLOCKED: no compatible backend found on 18081, 18080, or 8080."
  $blocked++
} else {
  Write-Host "OK: compatible backend=$backend"
}

if ($blocked -gt 0) {
  Write-Host "Live config preflight: blocked=$blocked (no external AI request was made)."
  exit 2
}
Write-Host "Live config preflight: ready (no external AI request was made)."
exit 0
