$ErrorActionPreference = "Stop"

$encryptionKey = if ([string]::IsNullOrWhiteSpace($env:STOCKAI_SECRETS_ENCRYPTION_KEY)) {
  $env:STOCKAI_AUTH_SECRET
} else {
  $env:STOCKAI_SECRETS_ENCRYPTION_KEY
}
if ([string]::IsNullOrWhiteSpace($encryptionKey)) {
  throw "Set STOCKAI_SECRETS_ENCRYPTION_KEY (or STOCKAI_AUTH_SECRET) in local-env.cmd before running account settings smoke. The backend intentionally refuses to store account API keys without encryption."
}

$baseUrl = if ([string]::IsNullOrWhiteSpace($env:STOCK_AI_BASE_URL)) {
  "http://localhost:8080/api/v1"
} else {
  $env:STOCK_AI_BASE_URL.TrimEnd("/")
}

Invoke-RestMethod -Uri "$baseUrl/health" -TimeoutSec 5 | Out-Null

$seed = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$email = "smoke-$seed@example.test"
$password = "Passw0rd!-smoke-$seed"

$authBody = @{
  email = $email
  password = $password
} | ConvertTo-Json

$register = Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -ContentType "application/json" -Body $authBody -SessionVariable webSession -TimeoutSec 15
if (-not $register.data.user.email) {
  throw "Register did not establish a session."
}

$login = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType "application/json" -Body $authBody -WebSession $webSession -TimeoutSec 15
if (-not $login.data.user.email) {
  throw "Login did not establish a session."
}

$initial = Invoke-RestMethod -Uri "$baseUrl/account/settings" -WebSession $webSession -TimeoutSec 15
if ($initial.data.preferredProvider -ne "" -or $initial.data.hasOpenAiApiKey -or $initial.data.hasGeminiApiKey -or $initial.data.hasDeepSeekApiKey) {
  throw "Initial account settings should be empty."
}

$saveBody = @{
  preferredProvider = "DEEPSEEK"
  openAiApiKey = "test-openai-$seed"
  geminiApiKey = "test-gemini-$seed"
  deepSeekApiKey = "test-deepseek-$seed"
} | ConvertTo-Json

$saved = Invoke-RestMethod -Uri "$baseUrl/account/settings" -Method Put -WebSession $webSession -ContentType "application/json" -Body $saveBody -TimeoutSec 15
if ($saved.data.preferredProvider -ne "DEEPSEEK" -or -not $saved.data.hasOpenAiApiKey -or -not $saved.data.hasGeminiApiKey -or -not $saved.data.hasDeepSeekApiKey) {
  throw "Saved account settings did not match expected values."
}

$partialBody = @{
  preferredProvider = "OPENAI"
} | ConvertTo-Json

$partial = Invoke-RestMethod -Uri "$baseUrl/account/settings" -Method Put -WebSession $webSession -ContentType "application/json" -Body $partialBody -TimeoutSec 15
if ($partial.data.preferredProvider -ne "OPENAI" -or -not $partial.data.hasOpenAiApiKey -or -not $partial.data.hasGeminiApiKey -or -not $partial.data.hasDeepSeekApiKey) {
  throw "Partial update should keep all stored keys."
}

$clearBody = @{
  geminiApiKey = ""
} | ConvertTo-Json

$cleared = Invoke-RestMethod -Uri "$baseUrl/account/settings" -Method Put -WebSession $webSession -ContentType "application/json" -Body $clearBody -TimeoutSec 15
if (-not $cleared.data.hasOpenAiApiKey -or $cleared.data.hasGeminiApiKey -or -not $cleared.data.hasDeepSeekApiKey) {
  throw "Expected OpenAI key kept and Gemini key cleared."
}

$loaded = Invoke-RestMethod -Uri "$baseUrl/account/settings" -WebSession $webSession -TimeoutSec 15
if ($loaded.data.preferredProvider -ne "OPENAI" -or -not $loaded.data.hasOpenAiApiKey -or $loaded.data.hasGeminiApiKey -or -not $loaded.data.hasDeepSeekApiKey) {
  throw "Reloaded account settings did not match expected final state."
}
if ([string]::IsNullOrWhiteSpace($loaded.data.updatedAt)) {
  throw "updatedAt should not be empty."
}

Write-Host "OK: auth register/login + account settings smoke passed."
Write-Host "user: $email"
Write-Host "preferredProvider: $($loaded.data.preferredProvider)"
Write-Host "openAi stored: $($loaded.data.hasOpenAiApiKey)"
Write-Host "gemini stored: $($loaded.data.hasGeminiApiKey)"
Write-Host "deepSeek stored: $($loaded.data.hasDeepSeekApiKey)"
