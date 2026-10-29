# SIGELAB · Programación II · UMG

Aplicación de escritorio Java Swing para administrar laboratorios, equipos,
préstamos, devoluciones parciales y mantenimiento, con SQL Server y reconocimiento
facial como segundo factor. Adaptada para **IntelliJ IDEA en Windows**.

**Integrantes:** completar con los nombres del grupo.

**Guía detallada:** docs/GUIA_EJECUCION_WINDOWS.pdf (también en Markdown).

## Qué incluye

- Login con BCrypt, estado del usuario y verificación facial obligatoria.
- Creación controlada del primer administrador y registro inicial de rostro.
- Roles ADMIN y ENCARGADO, comprobados también en los servicios.
- Usuarios, laboratorios, tipos y equipos; desactivación lógica de catálogos/usuarios.
- Cinco estados de equipo; el estado cambia por procesos, no por edición libre.
- Préstamos con múltiples equipos, devoluciones parciales y mantenimiento.
- QR en PNG y lectura desde imagen. El QR contiene el código y consulta el equipo real.
- Dashboard, reportes por fecha/estado, historial y auditoría.
- Esquema SQL, datos demo, pruebas y manuales.

**Estado de verificación:** compilación y empaquetado Maven correctos; 15 pruebas Java
y 18 Python aprobadas. Una prueba SQL Server está incluida pero no ejecutada aquí.
La cámara, el modelo InsightFace, la instalación Windows, Docker y las consultas
contra SQL Server real requieren validación en su equipo. No se afirma que los
18 casos de aceptación hayan sido ejecutados de extremo a extremo.

## Arquitectura de ejecución

- Java y Python se ejecutan directamente en Windows: Python necesita la webcam.
- SQL Server puede ejecutarse en Docker **o** en su instalación actual de Windows.
- Java se comunica con Python por `http://127.0.0.1:8765` usando un token local.
- Ambos utilizan la misma base SIGELAB. Docker no es necesario si usa SQL Server nativo.
- Horarios de negocio: Guatemala (UTC-6), independientemente del reloj del contenedor.

## 1. Requisitos

1. JDK 21; verificar `java -version` y `javac -version`.
2. IntelliJ IDEA con SDK del proyecto y Maven configurados para JDK 21.
3. Python 3.12 de 64 bits con lanzador `py`; verificar `py -3.12 --version`.
4. Microsoft ODBC Driver 18 de 64 bits, usado por Python.
5. SQL Server o Docker Desktop con contenedores Linux para la opción Compose.
6. Webcam accesible por Windows y conexión a Internet para dependencias/modelo.
7. Si InsightFace requiere compilar extensiones, Microsoft C++ Build Tools con
   herramientas C++ y Windows SDK. Esto no requiere cambiar de IDE para Java.

Fuentes oficiales:
- Python: https://www.python.org/downloads/windows/
- ODBC: https://learn.microsoft.com/en-us/sql/connect/odbc/download-odbc-driver-for-sql-server
- Herramientas C++: https://visualstudio.microsoft.com/downloads/
- SQL Server Docker: https://learn.microsoft.com/sql/linux/quickstart-install-connect-docker

## 2. Extraer y configurar

Extraiga el ZIP, abra PowerShell en la carpeta `sigelab` (donde está `pom.xml`) y ejecute:

```powershell
py -3.12 scripts/configurar.py
```

El asistente pide host, puerto TCP, usuario y contraseña de SQL Server. Genera una
clave de cifrado y un token compartido sin mostrarlos. Crea:

- `config/application.properties`: Java.
- `facial-service/.env`: Python.
- `.env`: Docker Compose.

Se ejecuta una sola vez; no reemplaza claves existentes. Para cambios posteriores,
edite los archivos locales. No cambie `FACIAL_ENCRYPTION_KEY` después de registrar
rostros: los perfiles existentes necesitan esa misma clave para descifrarse.

**Para el Docker incluido:** host localhost, puerto 14330 y usuario sa; elija una
contraseña propia de al menos 12 caracteres con mayúsculas, minúsculas, números y
símbolos. **Para SQL Server nativo:** use el puerto TCP real y un usuario SQL habilitado.
La configuración no utiliza autenticación integrada de Windows.

## 3. Preparar SQL Server — elegir una opción

### A. Docker (base dedicada)

```powershell
powershell -ExecutionPolicy Bypass -File scripts/inicializar-docker.ps1
```

Este comando aplica la política solo al proceso que ejecuta el script, no la cambia
permanentemente. Si la política de su equipo lo impide, siga la opción manual.
El script inicia SQL Server 2022 Developer, espera que responda y aplica los SQL.

Para revisar o detener:

```powershell
docker compose ps
docker compose logs --tail 80 sqlserver
docker compose stop
```

El puerto publicado es `127.0.0.1:14330`; el volumen conserva los datos. No ejecute
`docker compose down -v` si quiere conservarlos. Cambiar la contraseña del `.env`
no cambia la contraseña de una base que ya fue inicializada en el volumen.

### B. SQL Server instalado en Windows, o instalación manual

En SSMS conéctese a su instancia. Para el contenedor incluido, servidor `localhost,14330`,
usuario `sa` y la contraseña local. Ejecute los archivos en este orden:

1. `database/01_crear_base.sql`.
2. `database/02_esquema.sql` **solo la primera vez**.
3. `database/03_datos_demo.sql`.
4. `database/05_facial.sql`.
5. `database/04_comprobar.sql`.

Se esperan doce tablas: diez funcionales y dos faciales. Datos de ejemplo:
`CAM-001`, `PRO-001`, `LAP-001`, inicialmente disponibles.
No hay usuarios ni contraseñas predeterminados.

Si ya ejecutó la primera base entregada en este chat, el esquema funcional es
compatible: omita el script 02 y aplique 03 y 05. Los scripts no eliminan bases.
Si tiene otra estructura bajo el nombre SIGELAB, revise sus diferencias antes de
aplicarlos. Configure TCP/IP y autenticación SQL si su instancia nativa los necesita.

## 4. Instalar e iniciar reconocimiento facial

Desde la raíz del proyecto:

```powershell
powershell -ExecutionPolicy Bypass -File facial-service/setup-facial.ps1
powershell -ExecutionPolicy Bypass -File facial-service/start-facial.ps1
```

Mantenga la segunda terminal abierta. En el primer arranque, InsightFace puede
descargar el modelo `buffalo_l`; espere a que el servidor esté iniciado.
No copie el `.venv` de otro equipo o sistema operativo.

En otra terminal, comprobar:

```powershell
Set-Location facial-service
.\.venv\Scripts\python.exe -m scripts.health_check
Set-Location ..
```

Resultado esperado: `status: ok`, `modelLoaded: true`, `database: ok`.
El diagnóstico de cámara/modelo requiere el hardware y no fue ejecutado en el
entorno donde se preparó esta entrega. Si falla, consulte `docs/SOLUCION_PROBLEMAS.md`.

## 5. Abrir en IntelliJ IDEA

1. File → Open → seleccione `sigelab/pom.xml` y ábralo como proyecto Maven.
2. Project SDK: JDK 21. Maven runner/importer: JDK 21.
3. Recargue Maven y espere a que descargue las dependencias.
4. Ejecute `proyecto_sigelab.Launcher`.
5. En la configuración de ejecución, Working directory debe ser la carpeta
   `sigelab`, no `src` ni `target`, porque allí se busca `config/application.properties`.

También puede usar PowerShell:

```powershell
.\mvnw.cmd clean package
.\mvnw.cmd exec:java
```

Se incluye `dist/SIGELAB.jar`, ya compilado con sus dependencias Java:

```powershell
java -jar dist/SIGELAB.jar
```

Ejecute este comando desde la raíz `sigelab`. El JAR no incluye Python, el modelo
facial, SQL Server ni sus credenciales: esos componentes se preparan por separado.

## 6. Primer acceso

1. Pulse **Crear administrador inicial**; complete sus datos y contraseña.
2. Escriba ese usuario y contraseña en el login.
3. Pulse **Registrar mi rostro por primera vez** y siga las indicaciones de cámara.
4. Pulse **Ingresar con contraseña y rostro**; el rostro se verificará de nuevo.

El alta inicial solo funciona si no hay usuarios. Para usuarios posteriores, un ADMIN
los crea en Usuarios. La persona puede registrar su primer rostro usando sus propias
credenciales; reemplazar un rostro desde la aplicación requiere ADMIN.
Si el servicio facial falla o el rostro no coincide, no se abre el menú.

## 7. Pruebas y documentación

```powershell
.\mvnw.cmd test
Set-Location facial-service
.\.venv\Scripts\python.exe -m pytest -q
Set-Location ..
```

Para la prueba de integración use una base de pruebas con el esquema y los datos
iniciales instalados. Crea registros temporales propios y los elimina al finalizar:

```powershell
$env:SIGELAB_INTEGRATION='1'
.\mvnw.cmd -Dtest=SqlServerIntegrationTest test
Remove-Item Env:SIGELAB_INTEGRATION
```

- `docs/MANUAL_USUARIO.md`: operaciones y demostración.
- `docs/MANUAL_TECNICO.md`: arquitectura, reglas, transacciones e integración.
- `docs/MODELO_DATOS.md`: diagrama y diccionario.
- `docs/PRUEBAS.md`: matriz CP-01 a CP-18 y límites de validación.
- `docs/SOLUCION_PROBLEMAS.md`: instalación y errores frecuentes.

## Git y defensa

Esta entrega contiene código, no un historial de contribuciones del grupo. Integre
los archivos en su repositorio y registre commits reales mientras prueban y ajustan.
No suba `.env`, `config/application.properties`, `.venv`, el modelo descargado ni
registros con datos personales. Todos los integrantes deben comprender y poder
modificar el código; los manuales incluyen puntos concretos para preparar la defensa.
