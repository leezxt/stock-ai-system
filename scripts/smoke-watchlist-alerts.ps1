$ErrorActionPreference = "Stop"

$baseUrl = if ([string]::IsNullOrWhiteSpace($env:STOCK_AI_BASE_URL)) {
  "http://localhost:8080/api/v1"
} else {
  $env:STOCK_AI_BASE_URL.TrimEnd("/")
}

Invoke-RestMethod -Uri "$baseUrl/health" -TimeoutSec 5 | Out-Null
$seed = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$authBody = @{ email = "watch-alert-smoke-$seed@example.test"; password = "Passw0rd!-watch-alert-$seed" } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -ContentType "application/json" -Body $authBody -SessionVariable webSession -TimeoutSec 15 | Out-Null

Invoke-RestMethod -Uri "$baseUrl/watchlist" -Method Post -WebSession $webSession -ContentType "application/json" -Body (@{ market = "US"; symbol = "AAPL" } | ConvertTo-Json) -TimeoutSec 15 | Out-Null
$empty = Invoke-RestMethod -Uri "$baseUrl/watchlist/alerts" -WebSession $webSession -TimeoutSec 15
if ($null -eq $empty.data.rules -or $null -eq $empty.data.evaluations) {
  throw "watchlist alerts response is missing rules or evaluations"
}
$scheduler = Invoke-RestMethod -Uri "$baseUrl/watchlist/alerts/scheduler" -WebSession $webSession -TimeoutSec 15
if ($scheduler.data.notificationMode -ne "LOCAL_ONLY" -or [int64]$scheduler.data.fixedDelayMs -lt 1000) {
  throw "watchlist alert scheduler status is not fail-closed"
}
$preferences = Invoke-RestMethod -Uri "$baseUrl/watchlist/alerts/notification-preferences" -WebSession $webSession -TimeoutSec 15
if ($preferences.data.preferences.localEnabled -ne $true -or $preferences.data.externalChannelsAvailable -ne $false) {
  throw "watchlist alert notification preferences are not fail-closed"
}
$updatedPreferences = Invoke-RestMethod -Uri "$baseUrl/watchlist/alerts/notification-preferences" -Method Put -WebSession $webSession -ContentType "application/json" -Body (@{ localEnabled = $false; emailEnabled = $true } | ConvertTo-Json) -TimeoutSec 15
if ($updatedPreferences.data.preferences.localEnabled -ne $false -or $updatedPreferences.data.externalChannelsAvailable -ne $false) {
  throw "watchlist alert notification preference update failed"
}
Invoke-RestMethod -Uri "$baseUrl/watchlist/alerts/notification-preferences" -Method Put -WebSession $webSession -ContentType "application/json" -Body (@{ localEnabled = $true } | ConvertTo-Json) -TimeoutSec 15 | Out-Null

$ruleBody = @{ market = "US"; symbol = "AAPL"; condition = "PRICE_ABOVE"; threshold = 0; enabled = $true } | ConvertTo-Json
$created = Invoke-RestMethod -Uri "$baseUrl/watchlist/alerts" -Method Post -WebSession $webSession -ContentType "application/json" -Body $ruleBody -TimeoutSec 15
$ruleId = [string]$created.data.id
if ([string]::IsNullOrWhiteSpace($ruleId)) { throw "watchlist alert id is missing" }

$evaluated = Invoke-RestMethod -Uri "$baseUrl/watchlist/alerts" -WebSession $webSession -TimeoutSec 15
if (@($evaluated.data.evaluations).Count -ne 1 -or $evaluated.data.evaluations[0].status -ne "TRIGGERED") {
  throw "watchlist alert did not trigger deterministically"
}
if ($evaluated.data.newTriggerCount -ne 1 -or $evaluated.data.evaluations[0].newlyTriggered -ne $true -or @($evaluated.data.recentEvents).Count -ne 1) {
  throw "watchlist alert did not record its first trigger"
}
$notifications = Invoke-RestMethod -Uri "$baseUrl/watchlist/alerts/notifications?limit=10" -WebSession $webSession -TimeoutSec 15
if (@($notifications.data.notifications).Count -ne 1 -or $notifications.data.unreadCount -ne 1 -or $notifications.data.notifications[0].channel -ne "LOCAL_ONLY") {
  throw "watchlist alert notification queue did not record the trigger"
}
$notificationId = [string]$notifications.data.notifications[0].id
$readNotification = Invoke-RestMethod -Uri "$baseUrl/watchlist/alerts/notifications/$notificationId" -Method Put -WebSession $webSession -ContentType "application/json" -Body (@{ read = $true } | ConvertTo-Json) -TimeoutSec 15
if ($readNotification.data.state -ne "READ") { throw "watchlist alert notification was not marked read" }
$cleanupNotifications = Invoke-RestMethod -Uri "$baseUrl/watchlist/alerts/notifications/read" -Method Delete -WebSession $webSession -TimeoutSec 15
if ($cleanupNotifications.data.deleted -ne 1 -or $cleanupNotifications.data.localOnly -ne $true) { throw "watchlist alert read-notification cleanup failed" }

$refreshed = Invoke-RestMethod -Uri "$baseUrl/watchlist/alerts" -WebSession $webSession -TimeoutSec 15
if ($refreshed.data.newTriggerCount -ne 0 -or $refreshed.data.evaluations[0].newlyTriggered -ne $false -or @($refreshed.data.recentEvents).Count -ne 1) {
  throw "watchlist alert repeated the same trigger instead of deduplicating it"
}

$disabled = Invoke-RestMethod -Uri "$baseUrl/watchlist/alerts/$ruleId" -Method Put -WebSession $webSession -ContentType "application/json" -Body (@{ enabled = $false } | ConvertTo-Json) -TimeoutSec 15
if ($disabled.data.enabled -ne $false) { throw "watchlist alert disable failed" }

Invoke-RestMethod -Uri "$baseUrl/watchlist/alerts/$ruleId" -Method Delete -WebSession $webSession -TimeoutSec 15 | Out-Null
$final = Invoke-RestMethod -Uri "$baseUrl/watchlist/alerts" -WebSession $webSession -TimeoutSec 15
if (@($final.data.rules).Count -ne 0) { throw "watchlist alert delete failed" }
$finalNotifications = Invoke-RestMethod -Uri "$baseUrl/watchlist/alerts/notifications" -WebSession $webSession -TimeoutSec 15
if (@($finalNotifications.data.notifications).Count -ne 0) { throw "watchlist alert notifications were not deleted with the rule" }

Write-Host "OK: watchlist alerts create/evaluate/disable/delete passed"
