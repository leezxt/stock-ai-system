$ErrorActionPreference = "Stop"

function Find-BackendBaseUrl {
  $candidates = @()
  if (-not [string]::IsNullOrWhiteSpace($env:STOCK_AI_BASE_URL)) {
    $candidates += $env:STOCK_AI_BASE_URL.TrimEnd("/")
  }
  $candidates += @(
    "http://localhost:18081/api/v1",
    "http://localhost:18080/api/v1",
    "http://localhost:8080/api/v1"
  )
  foreach ($candidate in ($candidates | Select-Object -Unique)) {
    try {
      $health = Invoke-RestMethod -Uri "$candidate/health" -TimeoutSec 4
      if ($health.status -ne "UP") { continue }
      $index = Invoke-RestMethod -Uri "$candidate" -TimeoutSec 4
      $endpoints = @($index.endpoints | ForEach-Object { [string]$_ })
      if ($index.name -eq "Stock AI API" -and
          ($endpoints -contains "GET /api/v1/stocks/{market}/{symbol}/prices")) {
        return $candidate
      }
    } catch {}
  }
  return $null
}

function Convert-ToInvariantDecimal($value, $label) {
  $text = ([string]$value).Trim().Replace(",", "").Replace("+", "")
  if ([string]::IsNullOrWhiteSpace($text) -or $text -in @("-", "--")) {
    throw "$label is empty"
  }
  try {
    return [decimal]::Parse(
      $text,
      [Globalization.NumberStyles]::Float,
      [Globalization.CultureInfo]::InvariantCulture
    )
  } catch {
    throw "$label is not numeric: $text"
  }
}

$baseUrl = Find-BackendBaseUrl
if ([string]::IsNullOrWhiteSpace($baseUrl)) {
  Write-Host "BLOCKED: no compatible backend found on 18081, 18080, or 8080."
  exit 2
}

$twseUrl = if ([string]::IsNullOrWhiteSpace($env:STOCKAI_TWSE_STOCK_DAY_ALL_URL)) {
  "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL"
} else {
  $env:STOCKAI_TWSE_STOCK_DAY_ALL_URL.TrimEnd("/")
}

try {
  # Invoke-RestMethod returns the JSON array as one pipeline value in some
  # PowerShell versions; force enumeration so filtering works consistently.
  $rows = @(Invoke-RestMethod -Uri $twseUrl -TimeoutSec 20 | ForEach-Object { $_ })
} catch {
  Write-Host "BLOCKED: TWSE STOCK_DAY_ALL is unreachable: $($_.Exception.Message)"
  exit 2
}

$twseRow = @($rows | Where-Object { [string]$_.Code -eq "2330" }) | Select-Object -First 1
if ($null -eq $twseRow) {
  throw "TWSE STOCK_DAY_ALL did not contain code 2330"
}
$officialClose = Convert-ToInvariantDecimal $twseRow.ClosingPrice "TWSE 2330 ClosingPrice"
if ($officialClose -le 0) {
  throw "TWSE 2330 ClosingPrice must be positive"
}

$pricesResponse = Invoke-RestMethod -Uri "$baseUrl/stocks/TW/2330.TW/prices?range=1M" -TimeoutSec 30
$bars = @($pricesResponse.data.prices | ForEach-Object { $_ })
if ($bars.Count -lt 2) {
  throw "backend returned fewer than two dated TWSE history points: $($bars.Count)"
}

$previousDate = $null
$sources = [System.Collections.Generic.HashSet[string]]::new()
foreach ($bar in $bars) {
  if ([string]::IsNullOrWhiteSpace([string]$bar.date)) {
    throw "backend price history contains a missing date"
  }
  $date = [DateTime]::ParseExact([string]$bar.date, "yyyy-MM-dd", [Globalization.CultureInfo]::InvariantCulture)
  if ($null -ne $previousDate -and $date -le $previousDate) {
    throw "backend price history dates are not strictly increasing"
  }
  $previousDate = $date
  $close = Convert-ToInvariantDecimal $bar.close "backend price close"
  if ($close -le 0) {
    throw "backend price history contains a non-positive close"
  }
  [void]$sources.Add([string]$bar.source)
}
if ($sources.Contains("mock")) {
  throw "backend price history unexpectedly contains mock data: $($sources -join ',')"
}

$summary = Invoke-RestMethod -Uri "$baseUrl/stocks/TW/2330.TW/summary" -TimeoutSec 30
$summaryPrice = Convert-ToInvariantDecimal $summary.data.priceSnapshot.lastPrice "backend summary lastPrice"
if ([math]::Abs([double]($summaryPrice - $officialClose)) -gt 0.01) {
  throw "TWSE/backend latest price mismatch: official=$officialClose backend=$summaryPrice"
}

$firstDate = [string]$bars[0].date
$lastDate = [string]$bars[$bars.Count - 1].date
Write-Host "OK: TWSE 2330 official=$officialClose backend=$summaryPrice summarySource=$($summary.data.source) history=$($bars.Count) points=$firstDate..$lastDate sources=$($sources -join ',') base=$baseUrl"
