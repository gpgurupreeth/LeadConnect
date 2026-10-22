<#
.SYNOPSIS
  Manage the local WAHA container running in WSL 2.

.DESCRIPTION
  Docker Engine is installed inside WSL 2 Ubuntu, not Docker Desktop.
  This wrapper invokes waha.sh inside WSL as root so it can access Docker.

.EXAMPLE
  .\manage-waha.ps1 -Action up
  .\manage-waha.ps1 -Action health
  .\manage-waha.ps1 -Action qr
  .\manage-waha.ps1 -Action logs
#>
[CmdletBinding()]
param(
    [ValidateSet("up", "down", "restart", "status", "logs", "wait-health", "health", "start-session", "restart-session", "qr", "fresh-qr")]
    [string]$Action = "status",
    [string]$Session = "default"
)

$ErrorActionPreference = "Stop"

$bashScript = Join-Path $PSScriptRoot "waha.sh"
if (-not (Test-Path $bashScript)) {
    throw "Missing $bashScript"
}

$wslScript = (wsl -e wslpath -a "$bashScript").Trim()
if ([string]::IsNullOrWhiteSpace($wslScript)) {
    throw "Could not translate the WAHA helper path into WSL."
}

wsl -u root -e bash "$wslScript" "$Action" "$Session"
if ($LASTEXITCODE -ne 0) {
    throw "WAHA command '$Action' failed with exit code $LASTEXITCODE."
}
