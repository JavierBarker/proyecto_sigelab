param()
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    docker compose up -d
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar Docker Compose.' }
    Write-Host 'Esperando SQL Server (hasta 150 segundos)...'
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        docker compose exec -T sqlserver bash -lc 'export SQLCMDPASSWORD="$MSSQL_SA_PASSWORD"; /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -i /scripts/00_health.sql -o /dev/null' 2>$null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 5
    }
    if (-not $ready) { throw 'SQL Server no respondió. Revise docker compose logs sqlserver.' }
    docker compose exec -T sqlserver bash -lc 'export SQLCMDPASSWORD="$MSSQL_SA_PASSWORD"; /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -i /scripts/01_crear_base.sql'
    if ($LASTEXITCODE -ne 0) { throw 'Falló la creación de SIGELAB.' }
    # El esquema inicial solo se ejecuta si no existe Usuario.
    docker compose exec -T sqlserver bash -lc 'export SQLCMDPASSWORD="$MSSQL_SA_PASSWORD"; /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -i /scripts/00_sin_esquema.sql' 2>$null
    if ($LASTEXITCODE -eq 0) {
        docker compose exec -T sqlserver bash -lc 'export SQLCMDPASSWORD="$MSSQL_SA_PASSWORD"; /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -i /scripts/02_esquema.sql'
        if ($LASTEXITCODE -ne 0) { throw 'Falló el esquema inicial.' }
    } else {
        Write-Host 'El esquema ya existe o no se pudo comprobar. No se reemplazan tablas; los pasos siguientes detectarán incompatibilidades.'
    }
    foreach ($script in @('03_datos_demo.sql','05_facial.sql','04_comprobar.sql')) {
        $command = 'export SQLCMDPASSWORD="$MSSQL_SA_PASSWORD"; /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -i /scripts/' + $script
        docker compose exec -T sqlserver bash -lc $command
        if ($LASTEXITCODE -ne 0) { throw "Falló $script. Se detuvo la instalación sin borrar datos." }
    }
    Write-Host 'Base preparada. Configure e inicie el componente facial y abra SIGELAB.'
} finally { Pop-Location }
