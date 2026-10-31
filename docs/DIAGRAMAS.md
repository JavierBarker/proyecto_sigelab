# Diagramas de SIGELAB

Diagramas de la documentación, explicados para cualquier persona **aunque no conozca el proyecto**. Cada sección tiene: qué muestra el diagrama, la imagen PNG lista para el entregable y la fuente Mermaid (GitHub la dibuja automáticamente).

Para regenerar los PNG después de cambiar el esquema o un proceso:

```powershell
java -Djava.awt.headless=true scripts/GenerarDiagramas.java
```

---

## 1. Flujo de inicio de sesión

**Qué muestra:** cómo entra una persona al sistema. Hay dos puertas: la contraseña (guardada con hash BCrypt) y el rostro (verificado por el servicio de Python con la cámara). Si cualquiera de las dos falla, o si el servicio facial está apagado, el acceso se **niega** (regla de falla cerrada). Cada intento queda en auditoría.

![Flujo de login](diagramas/flujo-login.png)

```mermaid
flowchart TD
    A([El usuario abre SIGELAB]) --> B[Ventana de inicio de sesión:<br/>usuario y contraseña]
    B --> C{¿Hay usuarios registrados?}
    C -- "No (primer arranque)" --> D[Crear el primer ADMIN:<br/>la contraseña se guarda como hash BCrypt]
    D --> E[«Registrar mi rostro»:<br/>la cámara guarda el vector facial cifrado]
    E --> B
    C -- Sí --> F{¿Contraseña correcta (BCrypt)<br/>y usuario ACTIVO?}
    F -- No --> G[Mensaje: usuario o contraseña<br/>incorrectos, o usuario inactivo]
    G --> H[(Auditoría: LOGIN_FALLIDO)] --> I([Acceso denegado])
    F -- Sí --> J[Llama al servicio facial local<br/>Python · 127.0.0.1:8765 · «Mire a la cámara…»]
    J --> K[La cámara captura el rostro,<br/>comprueba la prueba de vida (liveness)<br/>y compara con el perfil registrado]
    K --> L{¿El rostro coincide?}
    L -- "No / servicio caído" --> M[Mensaje claro + regla de falla cerrada:<br/>si no se pudo verificar, NO se admite]
    M --> N[(Auditoría: LOGIN_FALLIDO)] --> I
    L -- Sí --> O[(Auditoría: LOGIN_EXITOSO)]
    O --> P([Se abre la ventana principal<br/>con el menú según el rol])
```

---

## 2. Flujo de préstamo

**Qué muestra:** cómo se presta uno o varios equipos. Los equipos se agregan por código o leyendo su QR (el QR **solo identifica**, no cambia estados). Al confirmar, todo ocurre en **una sola transacción**: o se crea el préstamo completo con sus equipos en PRESTADO, o no se crea nada (rollback). Se re-verifica la disponibilidad dentro de la transacción para que dos usuarios no puedan prestar el mismo equipo a la vez.

![Flujo de préstamo](diagramas/flujo-prestamo.png)

```mermaid
flowchart TD
    A([«Nuevo préstamo»]) --> B[Escribir solicitante, fecha de devolución esperada<br/>y observaciones]
    B --> C[Agregar equipo por código<br/>o «Leer QR de imagen»]
    C --> D[(Buscar el equipo en SQL Server.<br/>El QR solo identifica: no cambia estados)]
    D --> E{¿Existe, está DISPONIBLE<br/>y no está repetido?}
    E -- No --> F[Mensaje: «EQ-X está PRESTADO…<br/>y no puede prestarse» / «no existe» / «ya fue agregado»]
    F --> G([Se quita o se corrige])
    E -- Sí --> H[El equipo se agrega a la lista temporal]
    H --> I{¿Agregar otro equipo?}
    I -- Sí --> C
    I -- No --> J["TRANSACCIÓN: 1) bloquear equipos (UPDLOCK) 2) verificar DISPONIBLES<br/>3) insertar Prestamo 4) insertar DetallePrestamo por equipo<br/>5) equipos pasan a PRESTADO 6) auditoría PRESTAMO_CREADO 7) COMMIT"]
    J --> K{¿Todo se completó sin errores?}
    K -- No --> L[ROLLBACK total: no queda encabezado, ni detalles,<br/>ni equipos PRESTADOS, ni auditoría]
    L --> M([Préstamo rechazado, nada a medias])
    K -- Sí --> N([«Préstamo #N creado» · equipos PRESTADOS])
```

---

## 3. Flujo de devolución

**Qué muestra:** cómo regresan los equipos. Puede ser **parcial** (hoy devuelvo 2 de 3) o completa. Cada equipo se devuelve como BUENO (vuelve a DISPONIBLE) o DANADO (queda DAÑADO y no se presta hasta pasar por mantenimiento). Un equipo nunca se devuelve dos veces y el historial nunca se borra: el préstamo se cierra solo cuando no queda nada pendiente.

![Flujo de devolución](diagramas/flujo-devolucion.png)

```mermaid
flowchart TD
    A([Seleccionar préstamo →<br/>«Ver detalle / devolver»]) --> B[(El sistema muestra los equipos<br/>y cuáles siguen pendientes)]
    B --> C{¿El préstamo está ACTIVO?}
    C -- No --> D[Mensaje: el préstamo no está activo] --> E([Rechazado])
    C -- Sí --> F[Seleccionar uno o varios pendientes<br/>y elegir condición BUENO o DANADO]
    F --> G["TRANSACCIÓN: verificar pendiente (nunca dos veces) · insertar Devolucion<br/>· marcar fecha en el detalle · equipo pasa a DISPONIBLE (BUENO)<br/>o DANADO (dañado) · auditoría"]
    G --> H{¿Se intentó devolver algo<br/>ya devuelto o ajeno al préstamo?}
    H -- Sí --> I[Rechazo y ROLLBACK completo<br/>de esa devolución] --> J([Rechazado])
    H -- No --> K{¿Quedan equipos pendientes?}
    K -- Sí --> L([Devolución PARCIAL:<br/>el préstamo sigue ACTIVO])
    K -- No --> M[El préstamo pasa a FINALIZADO] --> N([Devolución completa registrada])
```

---

## 4. Flujo de mantenimiento

**Qué muestra:** las dos mitades del mantenimiento. **Enviar:** solo equipos DISPONIBLES (preventivo) o DAÑADOS (correctivo) pueden entrar; el equipo queda en MANTENIMIENTO y bloqueado para préstamos. **Finalizar:** el técnico elige el resultado: REPARADO (el equipo vuelve a DISPONIBLE) o NO_REPARABLE (pasa a BAJA y se retira del servicio para siempre).

![Flujo de mantenimiento](diagramas/flujo-mantenimiento.png)

```mermaid
flowchart TD
    subgraph A["PARTE A · Enviar equipo"]
        A1([«Enviar equipo»]) --> A2[Elegir equipo, tipo PREVENTIVO/CORRECTIVO,<br/>descripción y responsable]
        A2 --> A3{¿Está DISPONIBLE o DANADO?}
        A3 -- No --> A4[Rechazado: PRESTADO, MANTENIMIENTO o BAJA<br/>no pueden entrar] --> A5([Rechazado])
        A3 -- Sí --> A6[("TRANSACCIÓN: Mantenimiento ABIERTO ·<br/>equipo pasa a MANTENIMIENTO ·<br/>auditoría EQUIPO_ENVIADO_MANTENIMIENTO")]
    end
    subgraph B["PARTE B · Finalizar"]
        A6 --> B1([«Finalizar»])
        B1 --> B2{Resultado de la revisión}
        B2 -- REPARADO --> B3[El equipo vuelve a DISPONIBLE<br/>y puede prestarse de nuevo]
        B2 -- NO_REPARABLE --> B4[El equipo pasa a BAJA:<br/>fin de su vida útil]
        B3 --> B5[("Mantenimiento FINALIZADO con fecha de salida ·<br/>auditoría MANTENIMIENTO_FINALIZADO")]
        B4 --> B5
        B5 --> B6([Fin del mantenimiento])
    end
```

---

## 5. Ciclo de vida del equipo (máquina de estados)

**Qué muestra:** los 5 estados por los que pasa cada equipo y el único camino permitido entre ellos. Es el diagrama clave de la defensa: si se pregunta «¿qué pasa si devuelven dañado un equipo?», la respuesta está aquí. El estado **nunca** se edita a mano en el formulario: solo cambian los procesos.

![Estados del equipo](diagramas/estados-equipo.png)

```mermaid
stateDiagram-v2
    [*] --> DISPONIBLE : alta de equipo nuevo
    DISPONIBLE --> PRESTADO : confirmar préstamo
    PRESTADO --> DISPONIBLE : devolución BUENO
    PRESTADO --> DANADO : devolución DAÑADO
    DANADO --> MANTENIMIENTO : mantenimiento CORRECTIVO
    DISPONIBLE --> MANTENIMIENTO : mantenimiento PREVENTIVO
    MANTENIMIENTO --> DISPONIBLE : finaliza REPARADO
    MANTENIMIENTO --> BAJA : finaliza NO_REPARABLE
    BAJA --> [*]
```

---

## 6. Casos de uso

**Qué muestra:** quién puede hacer qué dentro del sistema. Hay dos roles reales: **ENCARGADO** (operación diaria: equipos, préstamos, devoluciones, mantenimiento, reportes) y **ADMIN**, que hereda todo lo del ENCARGADO y además administra usuarios, rostros, laboratorios, tipos de equipo y consulta la auditoría. El **servicio facial de Python** es un sistema externo que participa en el login y en el registro de rostros.

![Casos de uso](diagramas/casos-uso.png)

```mermaid
flowchart LR
    ADMIN(("ADMIN"))..->|hereda| ENC(("ENCARGADO"))
    subgraph SIGELAB["SIGELAB · sistema"]
        UC1(("Iniciar sesión<br/>(con rostro)"))
        UC2(("Cerrar sesión"))
        UC3(("Gestionar usuarios y roles"))
        UC4(("Registrar o reemplazar rostro"))
        UC5(("Gestionar laboratorios"))
        UC6(("Gestionar tipos de equipo"))
        UC7(("Consultar auditoría"))
        UC8(("Gestionar equipos y su QR"))
        UC9(("Registrar préstamo de varios equipos"))
        UC10(("Registrar devolución parcial o total"))
        UC11(("Enviar equipo a mantenimiento"))
        UC12(("Finalizar mantenimiento"))
        UC13(("Ver dashboard"))
        UC14(("Consultar reportes e historiales"))
        UC15(("Identificar equipo leyendo su QR"))
    end
    FACIAL(("Servicio facial Python<br/>(externo)"))
    ADMIN --> UC3 & UC4 & UC5 & UC6 & UC7 & UC1 & UC2
    ENC --> UC8 & UC9 & UC10 & UC11 & UC12 & UC13 & UC14 & UC15 & UC1 & UC2
    UC9 -.->|«incluye»| UC15
    UC8 -.->|«incluye»| UC15
    FACIAL -.->|verify / enroll<br/>HTTP 127.0.0.1| UC1
    FACIAL -.-> UC4
```

---

## 7. Modelo entidad-relación

**Qué muestra:** las 12 tablas de la base de datos (10 del sistema + 2 del componente facial) y cómo se relacionan. Un detalle importante: `Prestamo` y `Equipo` tienen una relación de muchos a muchos que se resuelve con `DetallePrestamo` (un préstamo trae varios equipos y un equipo aparece en varios préstamos a lo largo del tiempo). Este diagrama coincide exactamente con `database/02_esquema.sql` y `database/05_facial.sql`.

![Modelo entidad-relación](diagramas/modelo-er.png)

```mermaid
erDiagram
    Rol ||--o{ Usuario : clasifica
    Laboratorio ||--o{ Equipo : ubica
    TipoEquipo ||--o{ Equipo : categoriza
    Usuario ||--o{ Prestamo : registra
    Prestamo ||--|{ DetallePrestamo : incluye
    Equipo ||--o{ DetallePrestamo : participa
    DetallePrestamo ||--o| Devolucion : "se cierra con"
    Usuario ||--o{ Devolucion : recibe
    Equipo ||--o{ Mantenimiento : recibe
    Usuario ||--o{ Mantenimiento : registra
    Usuario o|--o{ Auditoria : genera
    Usuario ||--o| PerfilFacial : posee
    Usuario o|--o{ AuditoriaFacial : verifica
```

---

## Relación entre diagramas

- El **login** (1) es la única entrada; el menú que se abre depende del rol del **diagrama de casos de uso** (6).
- Un **préstamo** (2) mueve equipos de DISPONIBLE a PRESTADO; la **devolución** (3) los regresa como DISPONIBLE o DAÑADO; el **mantenimiento** (4) es el único camino de DAÑADO de vuelta a DISPONIBLE. Todos los caminos posibles están en la **máquina de estados** (5).
- Cada movimiento de (2), (3) y (4) escribe filas en las tablas del **modelo ER** (7) y queda registrado en `Auditoria`.
