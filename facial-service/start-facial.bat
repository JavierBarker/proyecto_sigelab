@echo off
setlocal
cd /d "%~dp0"
if not exist ".venv\Scripts\python.exe" (
  echo Cree primero el entorno con: .\setup-facial.ps1
  exit /b 1
)
if not exist ".env" (
  echo Falta facial-service\.env. Copie .env.example a .env y configure sus valores locales.
  exit /b 1
)
".venv\Scripts\python.exe" -m scripts.start
