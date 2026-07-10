@echo off
setlocal
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -Command "$healthy = $false; try { Invoke-RestMethod 'http://localhost:8080/api/v1/health' -TimeoutSec 1 | Out-Null; $healthy = $true } catch {}; if (-not $healthy) { Start-Process cmd -ArgumentList '/k','scripts\start-backend.cmd' -WindowStyle Normal; for ($i=0; $i -lt 30; $i++) { try { Invoke-RestMethod 'http://localhost:8080/api/v1/health' -TimeoutSec 1 | Out-Null; $healthy = $true; break } catch { Start-Sleep -Seconds 1 } } }; if ($healthy) { Write-Host 'Backend ready on http://localhost:8080/api/v1'; Start-Process 'http://localhost:8080/app' } else { Write-Host 'Backend did not become healthy within 30 seconds.' }"
