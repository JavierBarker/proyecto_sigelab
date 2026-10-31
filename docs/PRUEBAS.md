# Pruebas y evidencia

## Ejecución en el entorno de desarrollo de esta entrega

- Maven: compilación y empaquetado Java 21 correctos.
- JUnit: 15 pruebas aprobadas (RulesTest: 10; WorkflowTest: 5).
- SqlServerIntegrationTest: incluida y omitida por defecto; no hay SQL Server aquí.
- pytest: 18 pruebas aprobadas, sin webcam ni carga de InsightFace.
- No se realizaron pruebas visuales en Windows, ejecución Docker ni validación biométrica real.

Los reportes de estas ejecuciones se incluyen en `docs/evidencia/`.
No se presentan los casos manuales como aprobados. Complete la columna de evidencia
tras ejecutarlos en su computadora y conserve capturas si el catedrático las solicita.

## Matriz de aceptación de la guía

| Caso | Procedimiento y resultado esperado | Cobertura local | Evidencia en Windows/SQL/cámara |
|---|---|---|---|
| CP-01 | Credenciales válidas + rostro correcto → menú | Contrato de respuesta facial unitario | Pendiente |
| CP-02 | Credenciales válidas + otra persona → denegado | Respuesta matched=false rechazada | Pendiente |
| CP-03 | Crear segundo equipo con mismo inventario → rechazado | UNIQUE SQL; prueba SQL opcional escrita | Pendiente |
| CP-04 | Prestar disponible → PRESTADO | WorkflowTest + prueba SQL escrita | Pendiente |
| CP-05 | Prestar de nuevo el mismo → rechazado | WorkflowTest + prueba concurrente SQL escrita | Pendiente |
| CP-06 | Prestar DANADO → rechazado | RulesTest y ciclo de servicio | Pendiente |
| CP-07 | Prestar MANTENIMIENTO → rechazado | RulesTest | Pendiente |
| CP-08 | Prestar BAJA → rechazado | RulesTest y servicio | Pendiente |
| CP-09 | QR de disponible → datos y agregado | Generación/lectura PNG y servicio por separado | Pendiente |
| CP-10 | QR de prestado → bloqueado | Regla de disponibilidad, falta escenario UI real | Pendiente |
| CP-11 | Devolver BUENO → DISPONIBLE | WorkflowTest | Pendiente |
| CP-12 | Devolver DANADO → DANADO | WorkflowTest | Pendiente |
| CP-13 | Devolver dos veces → rechazado | WorkflowTest | Pendiente |
| CP-14 | Devolver dos de tres → un pendiente y ACTIVO | WorkflowTest + prueba SQL escrita | Pendiente |
| CP-15 | DANADO a mantenimiento → MANTENIMIENTO | WorkflowTest | Pendiente |
| CP-16 | Cierre REPARADO → DISPONIBLE | WorkflowTest | Pendiente |
| CP-17 | Cierre NO_REPARABLE → BAJA | WorkflowTest | Pendiente |
| CP-18 | Fecha esperada pasada con pendientes → VENCIDO | Consulta implementada; no ejecutada contra SQL | Pendiente |

Para CP-18 puede crear un préstamo con vencimiento dentro de un minuto y esperar
hasta que transcurra la fecha; después actualice Reportes → VENCIDO. No cambie el
reloj ni altere datos de producción para demostrarlo.

## Pruebas adicionales incluidas

- Préstamo vacío, equipo repetido, fecha pasada/igual: rechazo.
- Usuario inactivo y ENCARGADO intentando operación ADMIN: rechazo.
- Fallo de auditoría simulado: reversión de préstamo en repositorio de prueba.
- Segundo detalle inválido: reversión de la devolución completa en prueba.
- BCrypt acepta contraseña correcta y rechaza otra.
- QR PNG conserva exactamente CAM-001.
- Respuesta facial incompleta, ID distinto, liveness false, success false: rechazo.
- Token facial ausente, incorrecto o corto: rechazo.
- Cámara ocupada: rechazo de segunda sesión; liberación del bloqueo al fallar.
- Vector facial cifrado/descifrado y clave incorrecta.
- Similitud de vectores, lógica de giros y alias de las solicitudes.
- Mapeo de usuario SIGELAB y escape de contraseña en ODBC.

Las pruebas del repositorio en memoria no demuestran rollback real en SQL Server;
la prueba opcional sobre SQL verifica restricciones, devoluciones y concurrencia.

## Ejecutar prueba SQL opcional

Prepare un esquema SIGELAB vacío o de pruebas, aplique datos demo y configure Java.
La prueba crea un usuario, un laboratorio, un tipo y tres equipos con sufijo aleatorio;
borra solo esos registros al terminar. Si el proceso es terminado forzosamente,
pueden quedar registros TEST- para revisar manualmente.

```powershell
$env:SIGELAB_INTEGRATION='1'
.\mvnw.cmd -Dtest=SqlServerIntegrationTest test
Remove-Item Env:SIGELAB_INTEGRATION
```

## Registro para completar

- Fecha/hora:
- Integrante responsable:
- Versión/commit:
- Windows/JDK/Python/SQL Server:
- Cámara utilizada:
- Casos ejecutados y resultados:
- Errores observados y correcciones:
- Evidencia adjunta:
