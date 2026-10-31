param()

$ErrorActionPreference = "Stop"
$serviceRoot = $PSScriptRoot
$python = Join-Path $serviceRoot ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) { Write-Error "Falta .venv. Ejecute .\setup-facial.ps1." }

Push-Location $serviceRoot
try {
    & $python -m scripts.doctor
    if ($LASTEXITCODE -ne 0) { Write-Error "El doctor indicó que el equipo no está listo." }
    & $python -m scripts.health_check
    if ($LASTEXITCODE -ne 0) { Write-Error "La API health no está lista." }
    Write-Host "READY FOR DEMO"
}
finally {
    Pop-Location
}
