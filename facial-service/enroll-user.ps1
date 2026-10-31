param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Target,
    [switch]$Replace
)

$ErrorActionPreference = "Stop"
$serviceRoot = $PSScriptRoot
$python = Join-Path $serviceRoot ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) { Write-Error "Falta .venv. Ejecute .\setup-facial.ps1." }

$arguments = @("-m", "scripts.enroll_user")
if ($Target -match '^[0-9]+$') { $arguments += @("--user-id", $Target) }
else { $arguments += @("--username", $Target) }
if ($Replace) { $arguments += "--replace" }

Push-Location $serviceRoot
try {
    & $python @arguments
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
