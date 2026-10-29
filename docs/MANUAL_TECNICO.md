# Manual técnico

## Organización

`Launcher` compone configuración, conexión, repositorio, autenticación y controlador.
La vista es Swing puro; no hay formularios JavaFX ni instrumentación propietaria del IDE.

| Paquete | Responsabilidad |
|---|---|
| config | Configuración externa y conexiones JDBC |
| model | Records de entidades, tablas y enum de estados |
| view | Ventanas, formularios, tablas, préstamo y mensajes |
| controller | Eventos, coordinación y SwingWorker |
| service | Autenticación, permisos, validaciones y procesos |
| repository | SQL parametrizado y transacciones |
| integration | Cliente HTTP facial y ZXing |
| exception | Errores de negocio y de acceso a datos |

Flujo: View → Controller → Service → Repository → JDBC → SQL Server.
`Tasks` ejecuta trabajo JDBC/HTTP fuera del hilo de Swing; impide superposición en
la misma ventana. Los cuadros de préstamo utilizan su propio SwingWorker y botones
bloqueados durante la búsqueda/confirmación.

## Conexión y configuración

`AppConfig` lee UTF-8 desde config/application.properties. Puede sustituir valores
con variables `SIGELAB_DB_HOST`, `SIGELAB_DB_PORT`, `SIGELAB_DB_NAME`,
`SIGELAB_DB_USER`, `SIGELAB_DB_PASSWORD`, `SIGELAB_FACIAL_URL`, `SIGELAB_FACIAL_TOKEN`.
Los nombres siguen la regla: prefijo SIGELAB_ y clave en mayúsculas con puntos
convertidos en guiones bajos. Mantenga la misma base en Python y Java.

`ConnectionFactory` usa propiedades JDBC, sin concatenar la contraseña en una URL.
La conexión local cifra tráfico y permite confiar en el certificado autofirmado.
JDBC tiene timeout de conexión, socket y consulta. El pool no es necesario para el
alcance académico: las conexiones se abren por operación y se cierran con try-with-resources.

## Reglas y transacciones

`Rules` reúne validaciones puras; `InventoryService` coordina validaciones de estado,
autorización y cambios. `SqlRepository.transaction` comparte una sola conexión,
hace commit al finalizar y rollback si ocurre SQLException o RuntimeException.

Préstamo: validar solicitante, fecha, detalles no vacíos y sin duplicados → bloquear
los equipos por ID ordenado con UPDLOCK/HOLDLOCK → comprobar DISPONIBLE → insertar
encabezado y detalles → actualizar equipos → auditoría → commit.
El índice filtrado único de DetallePrestamo es una segunda barrera contra préstamos
duplicados. No reemplaza la validación del Service.

Devolución: bloquear encabezado y detalles → comprobar pertenencia, pendiente y
préstamo activo → comprobar PRESTADO → insertar Devolucion y fecha en detalle →
cambiar equipo → auditoría → cerrar encabezado solo si no quedan pendientes.
Si un detalle seleccionado es inválido, se revierte toda esa devolución.

Mantenimiento: validar equipo disponible/dañado → crear intervención → cambiar
estado. El cierre comprueba que siga abierto y cambia a DISPONIBLE o BAJA.
El índice filtrado de mantenimiento impide dos intervenciones abiertas del mismo equipo.

No se usan eliminaciones en cascada. No se permite actualizar libremente Estado de
Equipo desde el catálogo. Los identificadores SQL dinámicos proceden exclusivamente
de `Catalogo`, no de entradas del usuario; todos los valores usan PreparedStatement.

## Autenticación

`AuthService` comprueba BCrypt, ACTIVO y rol operador antes de llamar a Python.
Después de la captura vuelve a leer la cuenta y comprueba que no cambió la contraseña.
Solo entonces registra LOGIN_EXITOSO y crea Session. La sesión no se crea al comprobar
solo contraseña. Credenciales y segundo factor rechazados se auditan.

El primer administrador se crea bajo bloqueo de tabla si no existe ningún usuario.
Después, las altas requieren sesión ADMIN. La contraseña se convierte en hash BCrypt
con coste 12; no se almacena en texto plano. Un campo vacío al editar conserva el hash.
Las contraseñas nuevas tienen mínimo 8 caracteres y máximo 72 bytes UTF-8.

La comprobación de permisos se repite en cada servicio. Los menús ocultos son solo
una adaptación visual. ADMIN administra catálogos y usuarios; ENCARGADO opera equipos
y movimientos. El rol DOCENTE es opcional en la guía y no se ofrece en esta entrega.

## Integración facial

`FacialClient` usa HttpClient y Jackson. Exige token, destino local, HTTP correcto,
respuesta JSON válida, success, matched, livenessPassed y userId coincidente.
Los fallos se convierten en BusinessException; no existe un modo para omitir el rostro.

Python conserva el motor del segundo proyecto. La API abre procesos hijos para que
OpenCV controle su ventana desde el hilo principal. Un bloqueo en la API limita a
una sesión de captura. Uvicorn debe mantenerse con un único worker.

El token protege la API local; los permisos del usuario pertenecen a Java. Es un
protocolo académico para un equipo confiable, no una API pública. La clave AES-GCM
es distinta del token; debe persistir. Las fotos quedan en memoria, el vector se cifra
y se guarda en PerfilFacial. No hay garantía comercial anti-suplantación.

## QR y reportes

ZXing codifica el código de inventario. Leer un QR no modifica estados: el código
se consulta en el servicio; confirmar el préstamo vuelve a validar con bloqueo.
Se acepta imagen PNG/JPG con QR, conforme al alcance de la guía.

Las consultas usan JOIN, filtros, BETWEEN, COUNT, GROUP BY y ordenamiento. Vencido es
un cálculo por fecha y existencia de detalles pendientes. Las fechas SQL usan
DATEADD(HOUR,-6,SYSUTCDATETIME()) y Java America/Guatemala. No dependen de la zona
horaria UTC habitual del contenedor.

## Errores

DataAccessException traduce códigos frecuentes (duplicados, restricciones,
concurrencia, credenciales SQL). La interfaz muestra una explicación breve; el detalle
técnico de SQL se escribe al logger de Java. Los errores de negocio no se muestran
como trazas. Los fallos de cámara/servicio se rechazan, sin abrir el menú.

## Validación y límites

Consulte PRUEBAS.md. Las pruebas con repositorio en memoria acreditan coordinación
del servicio, pero no el comportamiento del motor SQL Server ni sus bloqueos.
SqlServerIntegrationTest prueba estos aspectos sobre una instancia configurada y
está desactivado por defecto. La webcam y el modelo requieren evaluación manual.
No se inspeccionó visualmente la interfaz Swing en Windows durante esta generación.

## Preguntas para la defensa

- ¿Por qué se vuelve a validar el equipo al confirmar? Puede haber cambiado desde la búsqueda.
- ¿Por qué DetallePrestamo? Permite varios equipos sin columnas equipo1/equipo2.
- ¿Qué sucede si falla auditoría durante un préstamo? La transacción revierte los cambios.
- ¿Cómo se decide una devolución parcial? Se cuentan detalles con FechaDevolucion nula.
- ¿Quién modifica Estado? Los procesos del servicio, no un campo editable.
- ¿Qué valida el rostro? La identidad del usuario cuya contraseña ya fue comprobada.
- ¿Dónde están las consultas? SqlRepository; las consultas faciales en el repositorio Python.

Fuentes técnicas: documentación oficial Microsoft de bloqueos UPDLOCK/HOLDLOCK,
JDBC y contenedores; documentación ZXing; código facial suministrado por el grupo.
