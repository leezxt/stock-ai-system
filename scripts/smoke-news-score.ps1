$ErrorActionPreference = "Stop"

$baseUrl = if ([string]::IsNullOrWhiteSpace($env:STOCK_AI_BASE_URL)) {
  "http://localhost:8080/api/v1"
} else {
  $env:STOCK_AI_BASE_URL.TrimEnd("/")
}

Invoke-RestMethod -Uri "$baseUrl/health" -TimeoutSec 5 | Out-Null

function Assert-Unauthorized($uri) {
  try {
    Invoke-RestMethod -Uri $uri -TimeoutSec 10 | Out-Null
  } catch {
    $response = $_.Exception.Response
    if ($null -eq $response) {
      throw
    }
    $status = [int]$response.StatusCode
    if ($status -ne 401) {
      throw "Expected 401 for $uri but received HTTP $status"
    }
    return
  }
  throw "Expected 401 for unauthenticated request: $uri"
}

$scorePath = "/stocks/TW/2330.TW/news-score?limit=5"
Assert-Unauthorized "$baseUrl$scorePath"

$seed = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$email = "news-score-smoke-$seed@example.test"
$secondEmail = "news-score-other-$seed@example.test"
$password = "Passw0rd!-news-$seed"
$authBody = @{ email = $email; password = $password } | ConvertTo-Json
$secondAuthBody = @{ email = $secondEmail; password = $password } | ConvertTo-Json

Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -ContentType "application/json" -Body $authBody -SessionVariable webSession -TimeoutSec 15 | Out-Null
Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -ContentType "application/json" -Body $secondAuthBody -SessionVariable otherSession -TimeoutSec 15 | Out-Null

$publishedAt = @(
  "2026-08-07T00:00:00Z",
  "2026-08-06T00:00:00Z",
  "2026-07-20T00:00:00Z"
)
$documents = @(
  @{
    symbol = "2330"
    market = "TW"
    docType = "NEWS"
    title = "News score smoke ${seed}: outlook raised"
    source = "smoke-news-$seed"
    publishedAt = $publishedAt[0]
    content = "Demand is strong, growth is improving, and guidance was raised."
  },
  @{
    symbol = "2330"
    market = "TW"
    docType = "NEWS"
    title = "News score smoke ${seed}: supply-chain risk"
    source = "smoke-news-$seed"
    publishedAt = $publishedAt[1]
    content = "The company cut guidance because of tariff and lawsuit risk."
  },
  @{
    symbol = "2330"
    market = "TW"
    docType = "NEWS"
    title = "News score smoke ${seed}: routine announcement"
    source = "smoke-news-$seed"
    publishedAt = $publishedAt[2]
    content = "The company published a routine board meeting announcement."
  }
)

foreach ($document in $documents) {
  $body = $document | ConvertTo-Json -Depth 5
  $imported = Invoke-RestMethod -Uri "$baseUrl/documents/import" -Method Post -WebSession $webSession -ContentType "application/json" -Body $body -TimeoutSec 20
  if ([int]$imported.data.chunksImported -lt 1) {
    throw "News document import returned no chunks for '$($document.title)'."
  }
}

$score = Invoke-RestMethod -Uri "$baseUrl$scorePath" -WebSession $webSession -TimeoutSec 20
$data = $score.data
if ([int]$data.articleCount -lt 3) {
  throw "Expected at least 3 indexed news articles, received $($data.articleCount)."
}
if ([int]$data.bullishCount -lt 1 -or [int]$data.bearishCount -lt 1) {
  throw "Expected both bullish and bearish news signals. bullish=$($data.bullishCount), bearish=$($data.bearishCount)"
}
if ([int]$data.bullishCount + [int]$data.bearishCount + [int]$data.neutralCount -ne [int]$data.articleCount) {
  throw "News sentiment counts do not add up to articleCount."
}
if ($data.source -ne "rag-news-score-v1") {
  throw "Unexpected news score source: $($data.source)"
}
foreach ($article in @($data.articles)) {
  if ([string]::IsNullOrWhiteSpace($article.chunkId) -or [string]::IsNullOrWhiteSpace($article.source) -or [string]::IsNullOrWhiteSpace($article.rationale)) {
    throw "News score article is missing provenance or rationale."
  }
  if ($article.sentiment -notin @("BULLISH", "BEARISH", "NEUTRAL")) {
    throw "Unexpected article sentiment: $($article.sentiment)"
  }
}

$otherScore = Invoke-RestMethod -Uri "$baseUrl$scorePath" -WebSession $otherSession -TimeoutSec 20
if ([int]$otherScore.data.articleCount -ne 0 -or $otherScore.data.overallLabel -ne "INSUFFICIENT_DATA" -or $otherScore.data.source -ne "no-indexed-news") {
  throw "Owner isolation failed: the second account saw another account's indexed news."
}

Write-Host "OK: news score auth + import + evaluation + owner isolation smoke passed."
Write-Host "user: $email"
Write-Host "articles: $($data.articleCount)"
Write-Host "bullish/bearish/neutral: $($data.bullishCount)/$($data.bearishCount)/$($data.neutralCount)"
Write-Host "overall: $($data.overallScore) ($($data.overallLabel))"
Write-Host "confidence: $($data.confidence)"
