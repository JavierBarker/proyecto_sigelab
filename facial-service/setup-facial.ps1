param()

$ErrorActionPreference = "Stop"
$serviceRoot = $PSScriptRoot
$venvPython = Join-Path $serviceRoot ".venv\Scripts\python.exe"

try {
    & py -3.12 --version
    if ($LASTEXITCODE -ne 0) { throw "Python 3.12 no está instalado en el lanzador py." }
} catch {
    Write-Error "No se encontró Python 3.12. Instálelo desde python.org o use el lanzador 'py' con una instalación 3.12."
}

Push-Location $serviceRoot
try {
    if (-not (Test-Path $venvPython)) {
        & py -3.12 -m venv .venv
        if ($LASTEXITCODE -ne 0) { throw "No se pudo crear .venv." }
    }
    & $venvPython -m pip install --upgrade pip
    if ($LASTEXITCODE -ne 0) { throw "No se pudo actualizar pip." }
    & $venvPython -m pip install -r requirements.txt
    if ($LASTEXITCODE -ne 0) { throw "No se pudieron instalar requirements.txt." }
    & $venvPython -m pip show opencv-python-headless *> $null
    if ($LASTEXITCODE -eq 0) {
        & $venvPython -m pip uninstall -y opencv-python-headless
        if ($LASTEXITCODE -ne 0) { throw "No se pudo retirar opencv-python-headless." }
        & $venvPython -m pip install --force-reinstall --no-deps opencv-python==4.10.0.84
        if ($LASTEXITCODE -ne 0) { throw "No se pudo restaurar OpenCV con GUI." }
    }
    if (-not (Test-Path .env)) {
        Copy-Item .env.example .env
        Write-Warning "Se creó .env desde .env.example. Configure DB_PASSWORD y FACIAL_ENCRYPTION_KEY antes de usar el servicio."
    }
    Write-Host "Diagnóstico inicial (puede mostrar FAIL si SQL Server, ODBC, webcam o el modelo aún no están listos):"
    & $venvPython -m scripts.doctor
    if ($LASTEXITCODE -ne 0) { Write-Warning "El diagnóstico requiere pasos adicionales; revise la salida anterior." }
}
finally {
    Pop-Location
}
