$ErrorActionPreference = "Stop"

$baseUrl = if ([string]::IsNullOrWhiteSpace($env:STOCK_AI_BASE_URL)) {
  "http://localhost:8080/api/v1"
} else {
  $env:STOCK_AI_BASE_URL.TrimEnd("/")
}

$health = Invoke-RestMethod -Uri "$baseUrl/health" -TimeoutSec 5
$lineage = Invoke-RestMethod -Uri "$baseUrl/stocks/US/AAPL/data-lineage" -TimeoutSec 15
$data = $lineage.data

if ([string]::IsNullOrWhiteSpace([string]$data.symbol)) {
  throw "data-lineage symbol is missing"
}
if ([string]::IsNullOrWhiteSpace([string]$data.source)) {
  throw "data-lineage source is missing"
}
if (@("ADJUSTED_CLOSE", "UNADJUSTED_CLOSE", "SNAPSHOT_ONLY", "MOCK", "UNVERIFIED") -notcontains [string]$data.adjustmentStatus) {
  throw "unexpected adjustmentStatus: $($data.adjustmentStatus)"
}
if (@("OK", "PARTIAL", "INVALID", "MOCK") -notcontains [string]$data.integrityStatus) {
  throw "unexpected integrityStatus: $($data.integrityStatus)"
}
if ($null -eq $data.corporateActions) {
  throw "corporateActions must be an array"
}
if ($data.pricePointCount -lt 0) {
  throw "pricePointCount must not be negative"
}

Write-Host "OK: data-lineage symbol=$($data.symbol) source=$($data.source) adjustment=$($data.adjustmentStatus) integrity=$($data.integrityStatus) points=$($data.pricePointCount) actions=$(@($data.corporateActions).Count) health=$($health.status)"
