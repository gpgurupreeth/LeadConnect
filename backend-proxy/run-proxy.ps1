<#
.SYNOPSIS
  Starts the WhatsApp backend proxy on the port configured in .env.

.DESCRIPTION
  Reads PORT and HOST from backend-proxy\.env (defaults: 8081 / 0.0.0.0),
  verifies the local virtual environment, checks whether the port is already
  in use (port 8080 is commonly occupied by Apache/XAMPP), and starts uvicorn.

.EXAMPLE
  .\run-proxy.ps1

.EXAMPLE
  .\run-proxy.ps1 -Port 8082 -Reload
#>
[CmdletBinding()]
param(
    [int]$Port = 0,
    [string]$BindHost = "",
    [switch]$Reload
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

function Get-DotEnvValue {
    param([string]$Path, [string]$Key)

    if (-not (Test-Path $Path)) { return $null }

    $line = Get-Content -Path $Path -ErrorAction SilentlyContinue |
        Where-Object { $_ -match "^\s*$([regex]::Escape($Key))\s*=" } |
        Select-Object -First 1

    if (-not $line) { return $null }

    $value = ($line -split "=", 2)[1].Trim()
    return $value.Trim('"').Trim("'")
}

$envFile = Join-Path $scriptDir ".env"

if ($Port -le 0) {
    $envPort = Get-DotEnvValue -Path $envFile -Key "PORT"
    $Port = if ($envPort -and $envPort -match '^\d+$') { [int]$envPort } else { 8081 }
}

if ([string]::IsNullOrWhiteSpace($BindHost)) {
    $envHost = Get-DotEnvValue -Path $envFile -Key "HOST"
    $BindHost = if ([string]::IsNullOrWhiteSpace($envHost)) { "0.0.0.0" } else { $envHost }
}

if (-not (Test-Path $envFile)) {
    Write-Host "[warn] No .env file found at $envFile." -ForegroundColor Yellow
    Write-Host "       Copy .env.example to .env and set a strong JWT_SECRET first." -ForegroundColor Yellow
}

if ($Port -eq 8080) {
    Write-Host "[warn] Port 8080 is commonly occupied by Apache/XAMPP." -ForegroundColor Yellow
    Write-Host "       This project defaults to 8081. If binding fails, use 8081." -ForegroundColor Yellow
}

$python = Join-Path $scriptDir ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) {
    $python = (Get-Command python -ErrorAction SilentlyContinue).Source
}
if (-not $python) {
    Write-Host "[error] Python not found. Create the virtual environment first:" -ForegroundColor Red
    Write-Host "        python -m venv .venv" -ForegroundColor Yellow
    Write-Host "        .\.venv\Scripts\pip.exe install -r requirements.txt" -ForegroundColor Yellow
    exit 1
}

$existing = $null
try {
    $existing = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
} catch {
    # Older systems without Get-NetTCPConnection: let uvicorn report the bind error.
}
if ($existing) {
    $owner = ($existing | Select-Object -First 1).OwningProcess
    $procName = (Get-Process -Id $owner -ErrorAction SilentlyContinue).ProcessName
    Write-Host "[error] Port $Port is already in use by PID $owner ($procName)." -ForegroundColor Red
    Write-Host "        Stop that process or start with: .\run-proxy.ps1 -Port 8082" -ForegroundColor Yellow
    exit 1
}

$lanIps = @()
try {
    $lanIps = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254*" } |
        Select-Object -ExpandProperty IPAddress
} catch {
    # Non-Windows / restricted environments: skip the LAN hint.
}

Write-Host ""
Write-Host "Backend proxy starting" -ForegroundColor Green
Write-Host "  Local:  http://127.0.0.1:$Port/api/health"
foreach ($ip in $lanIps) {
    Write-Host "  Phone:  http://${ip}:$Port/api/health  (same Wi-Fi; use this URL in app Settings)"
}
Write-Host "  Stop:   Ctrl+C"
Write-Host ""

$uvicornArgs = @("-m", "uvicorn", "main:app", "--host", $BindHost, "--port", "$Port")
if ($Reload) { $uvicornArgs += "--reload" }

# Keep the app's own settings consistent with the actual bind address/port,
# including when -Port overrides .env.
$env:PORT = "$Port"
$env:HOST = $BindHost

& $python @uvicornArgs
