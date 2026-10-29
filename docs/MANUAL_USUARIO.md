# Manual de usuario

## Acceso y perfiles

En el primer arranque, cree el administrador inicial, registre su rostro y después
inicie sesión. Para todos los accesos se exige contraseña válida, usuario ACTIVO
y rostro coincidente. Si falla la cámara o Python, corrija el servicio y reintente.

ADMIN administra usuarios y catálogos y consulta auditoría. ENCARGADO opera equipos,
préstamos, devoluciones, mantenimiento y reportes. No se implementa DOCENTE porque
la guía lo define como opcional. La aplicación vuelve a comprobar permisos en cada
operación; un usuario desactivado no puede continuar operando con una ventana abierta.

## Usuarios

ADMIN: pestaña Usuarios → Nuevo. Registre nombre, apellido, usuario, correo,
contraseña, rol y estado. El usuario y su contraseña no son los de SQL Server.
Al editar, deje la contraseña vacía para conservarla. Para desactivar, cambie Estado
a INACTIVO. No puede desactivarse ni quitarse su propio rol ADMIN desde esa sesión.

El registro inicial de rostro puede hacerse desde el login con credenciales propias.
ADMIN también puede seleccionar una cuenta y registrar/reemplazar su rostro; debe
estar presente la persona correcta frente a la cámara.

## Laboratorios, tipos y equipos

ADMIN crea laboratorios y tipos. Sus códigos/nombres deben ser únicos; la capacidad
es positiva. La desactivación conserva relaciones e historial.

ADMIN o ENCARGADO crea un equipo seleccionando tipo y laboratorio activos. Registre
inventario, nombre, marca, modelo, serie opcional y observaciones. Se crea DISPONIBLE.
No se cambia el estado a mano. No se permite editar un equipo mientras está prestado
o en mantenimiento. Un código repetido se rechaza.

Seleccione un equipo y pulse Guardar QR para exportar PNG. El contenido es únicamente
su código de inventario. Elija una ubicación y confirme si reemplaza un archivo.

## Préstamo de uno o varios equipos

1. Préstamos y devoluciones → Nuevo préstamo.
2. Escriba solicitante, fecha esperada y observaciones.
3. Fecha: `AAAA-MM-DDTHH:mm`, por ejemplo `2026-10-30T17:00`, hora de Guatemala.
4. Escriba un código y pulse Buscar y agregar, o use Leer QR de imagen.
5. El sistema consulta SQL Server y rechaza equipos no disponibles o repetidos.
6. Repita para varios equipos. Puede quitar un equipo antes de confirmar.
7. Confirme: todos pasan a PRESTADO y se crea un solo encabezado con varios detalles.

Al confirmar se vuelve a revisar disponibilidad: si otra persona prestó un equipo
mientras armaba la lista, la operación completa se rechaza sin guardar parcialmente.

## Devoluciones parciales

Seleccione el préstamo → Ver detalle/devolver. Seleccione una o varias filas
pendientes; use Ctrl para selección múltiple. Pulse Devolver seleccionados y elija
BUENO o DANADO. Una misma operación aplica esa condición a la selección; para
condiciones diferentes, registre grupos separados.

BUENO cambia el equipo a DISPONIBLE; DANADO lo cambia a DANADO. El historial permanece.
Devolver dos de tres equipos mantiene el préstamo ACTIVO. Al devolver el último se
marca FINALIZADO. Una devolución repetida se rechaza.

## Mantenimiento y baja

Seleccione Enviar equipo en Mantenimiento. Puede elegir equipos DISPONIBLES o DANADOS,
tipo PREVENTIVO/CORRECTIVO, descripción, responsable y observaciones.
El equipo pasa a MANTENIMIENTO y queda bloqueado para préstamo.

Seleccione un mantenimiento ABIERTO → Finalizar:

- REPARADO: equipo DISPONIBLE.
- NO_REPARABLE: equipo BAJA.

No puede cerrar dos veces el mismo mantenimiento ni enviar un equipo PRESTADO o BAJA.

## Consultas

Resumen muestra totales y estados, préstamos activos y vencidos. Reportes permite:

- Equipos por estado.
- Préstamos por estado y rango de fechas del registro.
- Historial de préstamos y devoluciones de un equipo.
- Historial de sus mantenimientos.

VENCIDO se calcula cuando la fecha esperada ya pasó y existen equipos pendientes.
No se guarda como estado independiente: puede estar ACTIVO y vencido a la vez.
ADMIN consulta los últimos 1000 eventos en Auditoría, ordenados del más reciente.
Pulse Actualizar o cambie de pestaña para recargar; los datos no se refrescan en tiempo real.

## Demostración sugerida para la defensa

1. Ingrese con administrador y rostro correcto.
2. Muestre CAM-001 disponible y genere/lea su QR.
3. Preste CAM-001, PRO-001 y LAP-001 en un préstamo.
4. Intente prestar CAM-001 nuevamente: debe rechazarse.
5. Devuelva PRO-001 y LAP-001 buenos: queda un pendiente.
6. Devuelva CAM-001 dañada; el préstamo finaliza.
7. Intente prestarla: debe rechazarse.
8. Envíela a mantenimiento y ciérrelo como reparado.
9. Préstela otra vez y consulte historial y auditoría.
10. Compruebe por separado rostro incorrecto, usuario inactivo y mantenimiento no reparable.

No invente evidencias: registre fecha, equipo utilizado y resultados reales en la matriz.
