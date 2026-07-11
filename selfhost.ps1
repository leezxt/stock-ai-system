param(
    [ValidateSet("start", "stop", "status", "logs")]
    [string]$Action = "start"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

$docker = Get-Command docker -ErrorAction SilentlyContinue
if ($docker) {
    $dockerExe = $docker.Source
} else {
    $dockerExe = "C:\Program Files\Docker\Docker\resources\bin\docker.exe"
}

if (-not (Test-Path -LiteralPath $dockerExe)) {
    throw "找不到 Docker CLI。請先安裝並啟動 Docker Desktop。"
}

function New-RandomSecret {
    $bytes = [byte[]]::new(48)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return [Convert]::ToBase64String($bytes).TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

function Ensure-EnvValue([string]$Name) {
    $envPath = Join-Path $ProjectRoot ".env"
    if (-not (Test-Path -LiteralPath $envPath)) {
        New-Item -ItemType File -Path $envPath | Out-Null
    }
    $pattern = "^\s*" + [regex]::Escape($Name) + "\s*=\s*\S+"
    if (-not (Select-String -LiteralPath $envPath -Pattern $pattern -Quiet)) {
        Add-Content -LiteralPath $envPath -Encoding utf8 -Value ("{0}={1}" -f $Name, (New-RandomSecret))
        Write-Host "已在 .env 產生 $Name"
    }
}

function Get-EnvValue([string]$Name) {
    $match = Get-Content -LiteralPath (Join-Path $ProjectRoot ".env") |
        Select-String -Pattern ("^\s*" + [regex]::Escape($Name) + "\s*=") |
        Select-Object -Last 1
    if (-not $match) { return "" }
    return ($match.Line -split "=", 2)[1].Trim()
}

switch ($Action) {
    "start" {
        Ensure-EnvValue "STOCKAI_DATABASE_PASSWORD"
        Ensure-EnvValue "STOCKAI_AUTH_SECRET"
        Ensure-EnvValue "STOCKAI_SECRETS_ENCRYPTION_KEY"
        & $dockerExe info | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Docker Engine 尚未就緒，請先啟動 Docker Desktop。"
        }
        & $dockerExe compose up --detach postgres
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        $databasePassword = Get-EnvValue "STOCKAI_DATABASE_PASSWORD"
        $alterRole = "ALTER ROLE stock_ai WITH PASSWORD '$databasePassword';"
        $alterRole | & $dockerExe compose exec --no-TTY postgres psql --username stock_ai --dbname stock_ai
        if ($LASTEXITCODE -ne 0) {
            throw "無法同步 PostgreSQL 使用者密碼。"
        }
        & $dockerExe compose up --build --detach
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        & $dockerExe compose ps
        Write-Host "網站：http://localhost:$((Get-Content .env | Select-String '^STOCK_AI_HOST_PORT=' | ForEach-Object { ($_ -split '=', 2)[1].Trim() } | Select-Object -First 1) ?? '18080')/app"
    }
    "stop" {
        & $dockerExe compose down
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    "status" {
        & $dockerExe compose ps
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    "logs" {
        & $dockerExe compose logs --follow --tail 200
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
}
