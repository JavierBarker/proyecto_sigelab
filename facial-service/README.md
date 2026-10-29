# Reconocimiento facial de SIGELAB

Adaptación del segundo proyecto `universidadAsistenciaWithFaceRecon` suministrado
por el grupo. Conserva InsightFace, OpenCV, captura guiada, prueba de giro, cifrado
AES-GCM y ejecución interactiva en proceso hijo. Ya no consulta UniversidadAsistenciaDB.

**Instalación completa:** seguir `../README.md`. Ejecutar Python en Windows para
usar la webcam; SQL Server puede estar en Docker.

## API local

Todas las rutas requieren cabecera `X-API-Key` igual a `FACIAL_API_TOKEN` del `.env`.
El mismo valor se configura en `facial.token` para Java. El servicio se limita a
localhost, utiliza un único worker y rechaza capturas simultáneas.

| Método | Ruta | Entrada |
|---|---|---|
| GET | /health | Sin cuerpo |
| GET | /face/status/{userId} | ID de Usuario.Id |
| POST | /face/enroll | {"userId":1,"replace":false} |
| POST | /face/verify | {"userId":1} |
| DELETE | /face/profile/{userId} | {"adminUserId":1} |

El token autoriza al cliente local. Las contraseñas y permisos de los usuarios se
validan en los servicios Java; no se envían contraseñas a Python. Este protocolo no
es un servicio público multiusuario. Quien posee el token y las credenciales SQL
es un operador privilegiado del equipo local.

`success` significa que la operación pudo procesarse; no equivale a rostro correcto.
Java exige además `matched=true`, `livenessPassed=true` y el mismo `userId`.
Sin perfil, sin cámara, sin modelo o con rostro incorrecto, el acceso se rechaza.

## Persistencia

- `PerfilFacial.id_usuario` referencia `Usuario.Id` de SIGELAB.
- `get_user` obtiene el rol mediante JOIN con Rol y convierte Estado a activo.
- Se guarda el vector facial normalizado y cifrado, no las fotografías capturadas.
- `FACIAL_ENCRYPTION_KEY` debe conservarse junto con la copia de seguridad local.
- AuditoriaFacial registra resultados y errores; Auditoria registra acciones Java.

## Diagnóstico y pruebas

Desde esta carpeta:

```powershell
.\.venv\Scripts\python.exe -m scripts.doctor
.\.venv\Scripts\python.exe -m scripts.health_check
.\.venv\Scripts\python.exe -m pytest -q
```

El doctor puede abrir la cámara. Las pruebas unitarias no abren cámara ni consultan
SQL Server. En la entrega se ejecutaron 18 pruebas, sin cargar el modelo real.

El registro guiado usa frente y perfiles; el reto de vida utiliza giro de cabeza.
El umbral inicial de similitud es 0.55 y debe comprobarse con personas distintas,
la cámara real y su iluminación. No se afirma resistencia comercial contra suplantación.
La calidad de captura y la calibración quedan pendientes de ensayo físico.

## Cambios respecto al código recibido

- Consultas y migración adaptadas a SIGELAB.
- Token obligatorio para todas las rutas y enlace solo local.
- Bloqueo global de sesiones antes de crear procesos hijos.
- Tiempo máximo del proceso hijo 210 segundos; Java espera hasta 240.
- Auditoría admite errores de frames y de vista previa.
- Se cierran conexiones Python y se revierte al fallar la operación.
- Se protegen caracteres especiales en la cadena de conexión ODBC.
- Fechas de negocio ajustadas a Guatemala (UTC-6).
- Se excluyen las credenciales y el entorno virtual del ZIP original.

El token no sustituye las reglas de acceso de Java. Los scripts locales administrativos
son para el operador que instala el sistema; no son acciones del menú del ENCARGADO.
