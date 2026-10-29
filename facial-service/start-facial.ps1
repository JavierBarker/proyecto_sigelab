param()

$ErrorActionPreference = "Stop"
$serviceRoot = $PSScriptRoot
$python = Join-Path $serviceRoot ".venv\Scripts\python.exe"

if (-not (Test-Path $python)) {
    Write-Error "Cree primero el entorno con .\setup-facial.ps1"
}
if (-not (Test-Path (Join-Path $serviceRoot ".env"))) {
    Write-Error "Falta .env. Copie .env.example a .env y configure sus valores locales."
}

Push-Location $serviceRoot
try {
    & $python -m scripts.start
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
