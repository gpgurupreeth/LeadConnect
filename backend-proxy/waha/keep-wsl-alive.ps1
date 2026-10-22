<#
.SYNOPSIS
  Keep the WSL 2 VM alive while Docker/WAHA are in use.

.DESCRIPTION
  WSL 2 may shut down the Linux VM when the last wsl.exe command exits. That
  stops Docker and the WAHA container. This script holds a lightweight process
  open in WSL. Leave it running in its own terminal and stop it with Ctrl+C.

.EXAMPLE
  .\keep-wsl-alive.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

Write-Host "Keeping WSL alive for Docker/WAHA. Press Ctrl+C to stop."
Write-Host "If Docker or WAHA stops, run .\manage-waha.ps1 -Action up."

wsl -u root -e bash -lc "while true; do sleep 3600; done"
if ($LASTEXITCODE -ne 0) {
    throw "WSL keep-alive exited with code $LASTEXITCODE."
}
