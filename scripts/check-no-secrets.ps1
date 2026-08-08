param(
    [switch]$Staged
)

$ErrorActionPreference = "Stop"
$repoRoot = (git rev-parse --show-toplevel).Trim()
Push-Location $repoRoot

try {
    if ($Staged) {
        $files = @(git diff --cached --name-only --diff-filter=ACMR)
    } else {
        $files = @(git ls-files -co --exclude-standard)
    }

    $findings = [System.Collections.Generic.List[string]]::new()
    $protectedFilePattern = '(?i)^(local-env\.cmd|\.env(?:\..*)?|.*\.(pem|key|p12|pfx))$'
    $binaryExtensions = @('.7z', '.bmp', '.gif', '.ico', '.jar', '.jpeg', '.jpg', '.mp3', '.mp4', '.pdf', '.png', '.webp', '.zip')
    $tokenPattern = '\bsk-[A-Za-z0-9][A-Za-z0-9_-]{15,}\b'
    $assignmentPattern = '(?m)^\s*(?:set\s+["'']?|\$env:)?(?<name>OPENAI_API_KEY|STOCKAI_RAG_EMBEDDING_API_KEY|MIMO_API_KEY|ALPHAVANTAGE_API_KEY|FINMIND_API_TOKEN|FMP_API_KEY|STOCKAI_AUTH_SECRET|STOCKAI_SECRETS_ENCRYPTION_KEY|STOCKAI_DATABASE_PASSWORD)\s*(?:=|:)\s*(?<value>[^\r\n]+)'

    function Add-Finding([string]$message) {
        if (-not $findings.Contains($message)) {
            [void]$findings.Add($message)
        }
    }

    function Is-Placeholder([string]$value) {
        $normalized = $value.Trim().Trim('"').Trim("'")
        if ([string]::IsNullOrWhiteSpace($normalized)) { return $true }
        if ($normalized -match '(?i)^(your[-_].+|replace-with-.+|local-compose-.+|sk-your-real-key|stock_ai)$') { return $true }
        if ($normalized -match '^<.*>$') { return $true }
        if ($normalized -match '^(%[A-Za-z0-9_]+%|\$env:[A-Za-z0-9_]+|\$\{[A-Za-z0-9_]+(?::[-]?[^}]*)?\})$') { return $true }
        return $false
    }

    foreach ($relativePath in $files) {
        if ([string]::IsNullOrWhiteSpace($relativePath)) { continue }
        $relativePath = $relativePath.Trim()
        $fileName = [System.IO.Path]::GetFileName($relativePath)

        if ($fileName -match $protectedFilePattern) {
            Add-Finding "secret-bearing file is staged or visible to sync: $relativePath"
            continue
        }

        if ($binaryExtensions -contains ([System.IO.Path]::GetExtension($relativePath).ToLowerInvariant())) {
            continue
        }

        $content = $null
        try {
            if ($Staged) {
                $content = (git show --no-ext-diff --no-textconv ":$relativePath" 2>$null | Out-String)
            } else {
                $fullPath = Join-Path $repoRoot $relativePath
                if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { continue }
                $content = Get-Content -LiteralPath $fullPath -Raw -ErrorAction Stop
            }
        } catch {
            Add-Finding "unable to inspect file: $relativePath"
            continue
        }

        foreach ($match in [regex]::Matches($content, $tokenPattern)) {
            if ($match.Value -ne 'sk-your-real-key') {
                Add-Finding "OpenAI-style token detected in: $relativePath"
            }
        }

        foreach ($match in [regex]::Matches($content, $assignmentPattern)) {
            $value = $match.Groups['value'].Value
            if (-not (Is-Placeholder $value)) {
                Add-Finding "potential API secret assignment in $relativePath ($($match.Groups['name'].Value))"
            }
        }
    }

    if ($findings.Count -gt 0) {
        Write-Host "Secret scan failed. Remove the values below before syncing to GitHub:" -ForegroundColor Red
        foreach ($finding in $findings) {
            Write-Host "- $finding" -ForegroundColor Red
        }
        exit 1
    }

    $scope = if ($Staged) { 'staged content' } else { 'working-tree files that are not ignored' }
    Write-Host "Secret scan passed ($scope, $($files.Count) files)."
} finally {
    Pop-Location
}
