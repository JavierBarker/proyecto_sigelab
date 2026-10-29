# Solución de problemas

## Java / IntelliJ

**No encuentra application.properties:** Working directory debe ser la carpeta
que contiene pom.xml. El archivo se crea con scripts/configurar.py y no es un recurso
compilado dentro del JAR.

**SDK o compilación:** seleccione JDK 21 en Project SDK y Maven runner. Recargue Maven.
La aplicación usa Swing estándar; no requiere JavaFX ni archivos .form de IntelliJ.

**Maven no descarga:** revise Internet y proxy local. `dist/SIGELAB.jar` contiene
las dependencias Java ya empaquetadas; puede ejecutarlo con Java 21 desde la raíz.

## SQL Server / Docker

**Conexión rechazada:** compruebe servicio, TCP/IP y puerto publicado. El contenedor
usa 14330 fuera y 1433 dentro. Java/Python en Windows deben usar 14330.
`localhost,14330` es el formato de SSMS; en la configuración Java/Python, host y
puerto se escriben por separado.

**Credenciales rechazadas:** usuario SQL habilitado y contraseña correcta. Conectarse
con autenticación Windows en SSMS no acredita que `sa` esté habilitado. No cambie
credenciales de una instancia existente sin conocer su configuración.

**Contenedor unhealthy:** consulte `docker compose logs --tail 80 sqlserver`.
Compruebe contraseña conforme a SQL Server, recursos de Docker, puerto libre y que
Docker utilice contenedores Linux. No elimine el volumen para intentar corregir sin
antes evaluar si contiene datos importantes.

**Tablas no existen:** aplique 01, 02, 03 y 05 en SIGELAB. 02 es solo instalación inicial;
03 y 05 permiten repetirse sin borrar datos. `04_comprobar.sql` lista las tablas.

## Python / cámara

**No encuentra Python 3.12:** compruebe `py -0p` y `py -3.12 --version`. Los scripts
Windows de esta entrega utilizan el lanzador py y crean `.venv\Scripts\python.exe`.

**InsightFace falla al instalar:** conserve el error completo. Revise Microsoft C++
Build Tools, herramientas C++/Windows SDK y arquitectura de 64 bits. No sustituya
indiscriminadamente todas las versiones fijadas en requirements.txt.

**No encuentra ODBC Driver 18:** instálelo en Windows de 64 bits. JDBC y ODBC son
componentes distintos: que Java conecte no demuestra que Python pueda hacerlo.

**401/token inválido:** `facial.token` debe coincidir exactamente con FACIAL_API_TOKEN.
El configurador genera ambos. No confunda el token con FACIAL_ENCRYPTION_KEY.
El health_check incluido envía la cabecera; abrir /health sin ella será rechazado.

**Modelo no disponible/degraded:** el primer inicio puede descargar buffalo_l.
Compruebe conectividad y espacio, revise consola y use scripts.doctor. No declare
listo el sistema hasta que health devuelva ok.

**Cámara ocupada:** cierre aplicaciones que usen la webcam y termine la captura
anterior. La API atiende una sesión por vez. Puede configurar CAMERA_INDEX en .env.

**Ventana OpenCV no aparece:** compruebe que no haya quedado instalada únicamente
opencv-python-headless. El instalador incluido restaura opencv-python con GUI.
La API y la cámara corren en Windows, no en el contenedor SQL Server.

**No completa perfiles/giros:** mejore iluminación, sitúese frente a la cámara y siga
las instrucciones. No modifique el umbral para aceptar cualquier rostro. La validación
real debe incluir a una persona distinta, además del usuario registrado.

**No puede descifrar perfiles:** recupere la clave original usada al registrarlos.
Si no existe copia, un ADMIN deberá registrar nuevamente los perfiles afectados;
no cambie la clave en cada inicio.

## Reglas de negocio

**Equipo bloqueado:** revise Estado e historial. Para DANADO, abra mantenimiento y
ciérrelo como reparado antes de prestarlo. BAJA no vuelve a préstamo desde la interfaz.

**Préstamo permanece activo:** aún hay detalles pendientes; abra el detalle.
Una devolución no elimina el préstamo ni el equipo.

**Operación concurrente rechazada:** actualice y reintente. Los bloqueos y restricciones
impiden que dos operadores modifiquen el mismo equipo de forma incompatible.
