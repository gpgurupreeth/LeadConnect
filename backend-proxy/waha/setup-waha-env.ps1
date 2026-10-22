<#
.SYNOPSIS
  Generate WAHA server-side secrets and write LF-terminated .env files.

.DESCRIPTION
  Generates a 64-character hex WAHA API key and a random dashboard password.
  Writes backend-proxy/waha/.env and updates the WAHA block in
  backend-proxy/.env without printing either secret.

  The script writes LF line endings. CRLF values break WSL shell sourcing and
  produce malformed X-Api-Key headers.

.PARAMETER Force
  Replace an existing backend-proxy/waha/.env. This rotates the API key, so the
  WAHA container must be recreated and the proxy restarted afterward.

.EXAMPLE
  .\setup-waha-env.ps1

.EXAMPLE
  .\setup-waha-env.ps1 -Force
#>
[CmdletBinding()]
param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"

function New-HexSecret {
    param([int]$ByteCount)

    $buffer = New-Object byte[] $ByteCount
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($buffer)
    } finally {
        $rng.Dispose()
    }
    return (-join ($buffer | ForEach-Object { $_.ToString("x2") }))
}

function Write-LfFile {
    param([string]$Path, [string[]]$Lines)

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    $content = ($Lines -join "`n") + "`n"
    [System.IO.File]::WriteAllText($Path, $content, $utf8NoBom)
}

$wahaDir = $PSScriptRoot
$wahaEnv = Join-Path $wahaDir ".env"
$backendDir = Split-Path -Parent $wahaDir
$backendEnv = Join-Path $backendDir ".env"

if ((Test-Path $wahaEnv) -and -not $Force) {
    throw "$wahaEnv already exists. Re-run with -Force only if you intend to rotate the WAHA API key."
}

$apiKey = New-HexSecret -ByteCount 32
$dashboardPassword = New-HexSecret -ByteCount 24
$dashboardUser = "operator"

Write-LfFile -Path $wahaEnv -Lines @(
    "# Generated locally. Do not commit this file.",
    "WAHA_API_KEY=$apiKey",
    "WAHA_DASHBOARD_USERNAME=$dashboardUser",
    "WAHA_DASHBOARD_PASSWORD=$dashboardPassword"
)

if (Test-Path $backendEnv) {
    $existing = [System.IO.File]::ReadAllLines($backendEnv)
    $filtered = $existing | Where-Object {
        $_ -notmatch '^\s*WHATSAPP_PROVIDER\s*=' -and
        $_ -notmatch '^\s*WAHA_[A-Z0-9_]+\s*='
    }

    $wahaBlock = @(
        "",
        "# WhatsApp provider: local WAHA container (see backend-proxy/waha/README.md).",
        "WHATSAPP_PROVIDER=waha",
        "WAHA_ENABLE_LIVE=true",
        "WAHA_BASE_URL=http://127.0.0.1:3000",
        "WAHA_API_KEY=$apiKey",
        "WAHA_SESSION=default",
        "WAHA_VERIFY_SESSION=true"
    )

    Write-LfFile -Path $backendEnv -Lines (@($filtered) + $wahaBlock)
    Write-Host "Updated backend .env: $backendEnv"
} else {
    Write-Warning "Missing $backendEnv. Create it from .env.example, set JWT_SECRET, then run this script again."
}

Write-Host "Wrote WAHA .env: $wahaEnv"
Write-Host "WAHA dashboard username: $dashboardUser"
Write-Host "API key length: $($apiKey.Length); dashboard password length: $($dashboardPassword.Length)"
Write-Host "Secret values were not printed."
