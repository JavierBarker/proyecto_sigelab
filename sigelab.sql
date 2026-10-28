SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF DB_ID(N'SIGELAB') IS NULL
    CREATE DATABASE SIGELAB;
GO

USE SIGELAB;
GO

DROP TABLE IF EXISTS AuditoriaFacial;
DROP TABLE IF EXISTS PerfilFacial;
DROP TABLE IF EXISTS Auditoria;
DROP TABLE IF EXISTS Mantenimiento;
DROP TABLE IF EXISTS Devolucion;
DROP TABLE IF EXISTS DetallePrestamo;
DROP TABLE IF EXISTS Prestamo;
DROP TABLE IF EXISTS Equipo;
DROP TABLE IF EXISTS TipoEquipo;
DROP TABLE IF EXISTS Laboratorio;
DROP TABLE IF EXISTS Usuario;
DROP TABLE IF EXISTS Rol;
GO

CREATE TABLE Rol
(
    id_rol INT IDENTITY(1,1) NOT NULL,
    nombre VARCHAR(20) NOT NULL,
    descripcion VARCHAR(200) NULL,

    CONSTRAINT PK_Rol PRIMARY KEY (id_rol),
    CONSTRAINT UQ_Rol_Nombre UNIQUE (nombre),
    CONSTRAINT CK_Rol_Nombre
        CHECK (nombre IN ('ADMIN', 'ENCARGADO', 'DOCENTE'))
);
GO

CREATE TABLE Usuario
(
    id_usuario INT IDENTITY(1,1) NOT NULL,
    nombre VARCHAR(80) NOT NULL,
    apellido VARCHAR(80) NOT NULL,
    usuario VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    correo VARCHAR(120) NOT NULL,
    id_rol INT NOT NULL,
    estado VARCHAR(10) NOT NULL
        CONSTRAINT DF_Usuario_Estado DEFAULT 'ACTIVO',
    fecha_creacion DATETIME2(0) NOT NULL
        CONSTRAINT DF_Usuario_FechaCreacion DEFAULT SYSDATETIME(),

    CONSTRAINT PK_Usuario PRIMARY KEY (id_usuario),
    CONSTRAINT UQ_Usuario_Usuario UNIQUE (usuario),
    CONSTRAINT UQ_Usuario_Correo UNIQUE (correo),
    CONSTRAINT FK_Usuario_Rol
        FOREIGN KEY (id_rol) REFERENCES Rol(id_rol),
    CONSTRAINT CK_Usuario_Estado
        CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    CONSTRAINT CK_Usuario_Correo
        CHECK (correo LIKE '%_@_%._%')
);
GO

CREATE TABLE Laboratorio
(
    id_laboratorio INT IDENTITY(1,1) NOT NULL,
    codigo VARCHAR(20) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    ubicacion VARCHAR(150) NOT NULL,
    capacidad INT NOT NULL,
    estado VARCHAR(10) NOT NULL
        CONSTRAINT DF_Laboratorio_Estado DEFAULT 'ACTIVO',

    CONSTRAINT PK_Laboratorio PRIMARY KEY (id_laboratorio),
    CONSTRAINT UQ_Laboratorio_Codigo UNIQUE (codigo),
    CONSTRAINT CK_Laboratorio_Capacidad CHECK (capacidad > 0),
    CONSTRAINT CK_Laboratorio_Estado
        CHECK (estado IN ('ACTIVO', 'INACTIVO'))
);
GO

CREATE TABLE TipoEquipo
(
    id_tipo_equipo INT IDENTITY(1,1) NOT NULL,
    nombre VARCHAR(60) NOT NULL,
    descripcion VARCHAR(200) NULL,
    estado VARCHAR(10) NOT NULL
        CONSTRAINT DF_TipoEquipo_Estado DEFAULT 'ACTIVO',

    CONSTRAINT PK_TipoEquipo PRIMARY KEY (id_tipo_equipo),
    CONSTRAINT UQ_TipoEquipo_Nombre UNIQUE (nombre),
    CONSTRAINT CK_TipoEquipo_Estado
        CHECK (estado IN ('ACTIVO', 'INACTIVO'))
);
GO

CREATE TABLE Equipo
(
    id_equipo INT IDENTITY(1,1) NOT NULL,
    codigo_inventario VARCHAR(30) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    id_tipo_equipo INT NOT NULL,
    marca VARCHAR(60) NOT NULL,
    modelo VARCHAR(60) NOT NULL,
    numero_serie VARCHAR(60) NOT NULL,
    id_laboratorio INT NOT NULL,
    estado VARCHAR(15) NOT NULL
        CONSTRAINT DF_Equipo_Estado DEFAULT 'DISPONIBLE',
    observaciones VARCHAR(500) NULL,
    fecha_registro DATETIME2(0) NOT NULL
        CONSTRAINT DF_Equipo_FechaRegistro DEFAULT SYSDATETIME(),

    CONSTRAINT PK_Equipo PRIMARY KEY (id_equipo),
    CONSTRAINT UQ_Equipo_CodigoInventario UNIQUE (codigo_inventario),
    CONSTRAINT UQ_Equipo_NumeroSerie UNIQUE (numero_serie),
    CONSTRAINT FK_Equipo_TipoEquipo
        FOREIGN KEY (id_tipo_equipo) REFERENCES TipoEquipo(id_tipo_equipo),
    CONSTRAINT FK_Equipo_Laboratorio
        FOREIGN KEY (id_laboratorio) REFERENCES Laboratorio(id_laboratorio),
    CONSTRAINT CK_Equipo_Estado
        CHECK (estado IN ('DISPONIBLE', 'PRESTADO', 'DANADO', 'MANTENIMIENTO', 'BAJA'))
);
GO

CREATE TABLE Prestamo
(
    id_prestamo INT IDENTITY(1,1) NOT NULL,
    solicitante_nombre VARCHAR(150) NOT NULL,
    solicitante_identificacion VARCHAR(30) NOT NULL,
    fecha_prestamo DATETIME2(0) NOT NULL
        CONSTRAINT DF_Prestamo_FechaPrestamo DEFAULT SYSDATETIME(),
    fecha_esperada_devolucion DATETIME2(0) NOT NULL,
    id_usuario_registra INT NOT NULL,
    estado VARCHAR(12) NOT NULL
        CONSTRAINT DF_Prestamo_Estado DEFAULT 'ACTIVO',
    observaciones VARCHAR(500) NULL,

    CONSTRAINT PK_Prestamo PRIMARY KEY (id_prestamo),
    CONSTRAINT FK_Prestamo_Usuario
        FOREIGN KEY (id_usuario_registra) REFERENCES Usuario(id_usuario),
    CONSTRAINT CK_Prestamo_Estado
        CHECK (estado IN ('ACTIVO', 'FINALIZADO')),
    CONSTRAINT CK_Prestamo_Fechas
        CHECK (fecha_esperada_devolucion > fecha_prestamo)
);
GO

CREATE TABLE DetallePrestamo
(
    id_detalle_prestamo INT IDENTITY(1,1) NOT NULL,
    id_prestamo INT NOT NULL,
    id_equipo INT NOT NULL,
    estado VARCHAR(10) NOT NULL
        CONSTRAINT DF_DetallePrestamo_Estado DEFAULT 'PENDIENTE',

    CONSTRAINT PK_DetallePrestamo PRIMARY KEY (id_detalle_prestamo),
    CONSTRAINT UQ_DetallePrestamo_PrestamoEquipo UNIQUE (id_prestamo, id_equipo),
    CONSTRAINT FK_DetallePrestamo_Prestamo
        FOREIGN KEY (id_prestamo) REFERENCES Prestamo(id_prestamo),
    CONSTRAINT FK_DetallePrestamo_Equipo
        FOREIGN KEY (id_equipo) REFERENCES Equipo(id_equipo),
    CONSTRAINT CK_DetallePrestamo_Estado
        CHECK (estado IN ('PENDIENTE', 'DEVUELTO'))
);
GO

CREATE UNIQUE INDEX UX_DetallePrestamo_Pendiente
    ON DetallePrestamo(id_equipo)
    WHERE estado = 'PENDIENTE';
GO

CREATE TABLE Devolucion
(
    id_devolucion INT IDENTITY(1,1) NOT NULL,
    id_detalle_prestamo INT NOT NULL,
    fecha_devolucion DATETIME2(0) NOT NULL
        CONSTRAINT DF_Devolucion_Fecha DEFAULT SYSDATETIME(),
    condicion VARCHAR(10) NOT NULL,
    observaciones VARCHAR(500) NULL,
    id_usuario_registra INT NOT NULL,

    CONSTRAINT PK_Devolucion PRIMARY KEY (id_devolucion),
    CONSTRAINT UQ_Devolucion_Detalle UNIQUE (id_detalle_prestamo),
    CONSTRAINT FK_Devolucion_DetallePrestamo
        FOREIGN KEY (id_detalle_prestamo) REFERENCES DetallePrestamo(id_detalle_prestamo),
    CONSTRAINT FK_Devolucion_Usuario
        FOREIGN KEY (id_usuario_registra) REFERENCES Usuario(id_usuario),
    CONSTRAINT CK_Devolucion_Condicion
        CHECK (condicion IN ('BUENO', 'DANADO'))
);
GO

CREATE TABLE Mantenimiento
(
    id_mantenimiento INT IDENTITY(1,1) NOT NULL,
    id_equipo INT NOT NULL,
    fecha_ingreso DATETIME2(0) NOT NULL
        CONSTRAINT DF_Mantenimiento_FechaIngreso DEFAULT SYSDATETIME(),
    tipo VARCHAR(12) NOT NULL,
    descripcion VARCHAR(500) NOT NULL,
    responsable VARCHAR(120) NOT NULL,
    fecha_salida DATETIME2(0) NULL,
    resultado VARCHAR(15) NULL,
    estado VARCHAR(12) NOT NULL
        CONSTRAINT DF_Mantenimiento_Estado DEFAULT 'EN_PROCESO',
    observaciones VARCHAR(500) NULL,
    id_usuario_registra INT NOT NULL,

    CONSTRAINT PK_Mantenimiento PRIMARY KEY (id_mantenimiento),
    CONSTRAINT FK_Mantenimiento_Equipo
        FOREIGN KEY (id_equipo) REFERENCES Equipo(id_equipo),
    CONSTRAINT FK_Mantenimiento_Usuario
        FOREIGN KEY (id_usuario_registra) REFERENCES Usuario(id_usuario),
    CONSTRAINT CK_Mantenimiento_Tipo
        CHECK (tipo IN ('PREVENTIVO', 'CORRECTIVO')),
    CONSTRAINT CK_Mantenimiento_Resultado
        CHECK (resultado IS NULL OR resultado IN ('REPARADO', 'NO_REPARABLE')),
    CONSTRAINT CK_Mantenimiento_Estado
        CHECK (estado IN ('EN_PROCESO', 'FINALIZADO')),
    CONSTRAINT CK_Mantenimiento_Cierre
        CHECK
        (
            (estado = 'EN_PROCESO' AND fecha_salida IS NULL AND resultado IS NULL)
            OR
            (estado = 'FINALIZADO' AND fecha_salida IS NOT NULL AND resultado IS NOT NULL)
        ),
    CONSTRAINT CK_Mantenimiento_Fechas
        CHECK (fecha_salida IS NULL OR fecha_salida >= fecha_ingreso)
);
GO

CREATE UNIQUE INDEX UX_Mantenimiento_EnProceso
    ON Mantenimiento(id_equipo)
    WHERE estado = 'EN_PROCESO';
GO

CREATE TABLE Auditoria
(
    id_auditoria INT IDENTITY(1,1) NOT NULL,
    id_usuario INT NULL,
    fecha_hora DATETIME2(0) NOT NULL
        CONSTRAINT DF_Auditoria_FechaHora DEFAULT SYSDATETIME(),
    accion VARCHAR(40) NOT NULL,
    entidad VARCHAR(40) NOT NULL,
    id_registro INT NULL,
    descripcion VARCHAR(500) NOT NULL,

    CONSTRAINT PK_Auditoria PRIMARY KEY (id_auditoria),
    CONSTRAINT FK_Auditoria_Usuario
        FOREIGN KEY (id_usuario) REFERENCES Usuario(id_usuario),
    CONSTRAINT CK_Auditoria_Accion
        CHECK (accion IN
        (
            'LOGIN_EXITOSO',
            'LOGIN_FALLIDO',
            'USUARIO_CREADO',
            'USUARIO_ACTUALIZADO',
            'USUARIO_DESACTIVADO',
            'LABORATORIO_CREADO',
            'LABORATORIO_ACTUALIZADO',
            'TIPO_EQUIPO_CREADO',
            'TIPO_EQUIPO_ACTUALIZADO',
            'EQUIPO_CREADO',
            'EQUIPO_ACTUALIZADO',
            'PRESTAMO_CREADO',
            'PRESTAMO_FINALIZADO',
            'EQUIPO_DEVUELTO',
            'EQUIPO_ENVIADO_MANTENIMIENTO',
            'MANTENIMIENTO_FINALIZADO'
        ))
);
GO

CREATE INDEX IX_Equipo_Estado ON Equipo(estado);
CREATE INDEX IX_Equipo_Laboratorio ON Equipo(id_laboratorio);
CREATE INDEX IX_Prestamo_EstadoFecha ON Prestamo(estado, fecha_esperada_devolucion);
CREATE INDEX IX_DetallePrestamo_Equipo ON DetallePrestamo(id_equipo);
CREATE INDEX IX_Mantenimiento_Equipo ON Mantenimiento(id_equipo, fecha_ingreso DESC);
CREATE INDEX IX_Auditoria_FechaHora ON Auditoria(fecha_hora DESC);
GO

IF OBJECT_ID(N'dbo.PerfilFacial', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PerfilFacial
    (
        id_perfil_facial INT IDENTITY(1,1) NOT NULL,
        id_usuario INT NOT NULL,
        embedding VARBINARY(MAX) NOT NULL,
        modelo NVARCHAR(100) NOT NULL,
        dimension INT NOT NULL,
        fecha_registro DATETIME2(0) NOT NULL CONSTRAINT DF_PerfilFacial_FechaRegistro DEFAULT SYSDATETIME(),
        fecha_actualizacion DATETIME2(0) NOT NULL CONSTRAINT DF_PerfilFacial_FechaActualizacion DEFAULT SYSDATETIME(),
        activo BIT NOT NULL CONSTRAINT DF_PerfilFacial_Activo DEFAULT 1,
        cantidad_muestras INT NOT NULL,
        quality_score FLOAT NULL,
        version_algoritmo NVARCHAR(50) NOT NULL,

        CONSTRAINT PK_PerfilFacial PRIMARY KEY (id_perfil_facial),
        CONSTRAINT UQ_PerfilFacial_Usuario UNIQUE (id_usuario),
        CONSTRAINT FK_PerfilFacial_Usuario FOREIGN KEY (id_usuario)
            REFERENCES dbo.Usuario(id_usuario),
        CONSTRAINT CK_PerfilFacial_Dimension CHECK (dimension > 0),
        CONSTRAINT CK_PerfilFacial_Muestras CHECK (cantidad_muestras > 0)
    );
END;
GO

IF OBJECT_ID(N'dbo.AuditoriaFacial', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.AuditoriaFacial
    (
        id_auditoria BIGINT IDENTITY(1,1) NOT NULL,
        id_usuario INT NULL,
        fecha_hora DATETIME2(0) NOT NULL CONSTRAINT DF_AuditoriaFacial_Fecha DEFAULT SYSDATETIME(),
        accion VARCHAR(30) NOT NULL,
        resultado VARCHAR(20) NOT NULL,
        score FLOAT NULL,
        threshold FLOAT NULL,
        detalle NVARCHAR(1000) NULL,
        dispositivo NVARCHAR(200) NULL,

        CONSTRAINT PK_AuditoriaFacial PRIMARY KEY (id_auditoria),
        CONSTRAINT FK_AuditoriaFacial_Usuario FOREIGN KEY (id_usuario)
            REFERENCES dbo.Usuario(id_usuario)
            ON DELETE SET NULL,
        CONSTRAINT CK_AuditoriaFacial_Accion CHECK
            (accion IN ('ENROLL_INICIADO', 'ENROLL_EXITOSO', 'ENROLL_FALLIDO',
                        'VERIFY_INICIADO', 'VERIFY_EXITOSO', 'VERIFY_FALLIDO',
                        'LIVENESS_FALLIDO', 'CAMARA_NO_DISPONIBLE', 'PERFIL_ELIMINADO'))
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_AuditoriaFacial_UsuarioFecha'
               AND object_id = OBJECT_ID(N'dbo.AuditoriaFacial'))
BEGIN
    CREATE INDEX IX_AuditoriaFacial_UsuarioFecha
        ON dbo.AuditoriaFacial(id_usuario, fecha_hora DESC);
END;
GO

USE SIGELAB;
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

BEGIN TRANSACTION;

DECLARE @ahora DATETIME2(0) = SYSDATETIME();

INSERT INTO Rol (nombre, descripcion) VALUES
('ADMIN', 'Acceso completo: usuarios, catálogos, equipos, préstamos, devoluciones, mantenimiento, auditoría y reportes'),
('ENCARGADO', 'Operación diaria: equipos, préstamos, devoluciones, mantenimiento y consultas'),
('DOCENTE', 'Solo consultas');

DECLARE @rolAdmin INT = (SELECT id_rol FROM Rol WHERE nombre = 'ADMIN');
DECLARE @rolEncargado INT = (SELECT id_rol FROM Rol WHERE nombre = 'ENCARGADO');
DECLARE @rolDocente INT = (SELECT id_rol FROM Rol WHERE nombre = 'DOCENTE');

INSERT INTO Usuario (nombre, apellido, usuario, password_hash, correo, id_rol, estado) VALUES
('Ana', 'Morales', 'admin',
 '$2a$10$qT0c8pACs0czqevwz8Q8l.VrN7WCiFI4wjRipPhXTwsRD1bMejE3S', 'admin@sigelab.edu.gt', @rolAdmin, 'ACTIVO'),
('Luis', 'Pérez', 'encargado',
 '$2a$10$qyIuI6QA7hLq6cjjwfK5GevgourATNb1.OcuBHR5RxNyKjsF0FQ/C', 'encargado@sigelab.edu.gt', @rolEncargado, 'ACTIVO'),
('Carla', 'Méndez', 'docente',
 '$2a$10$9cpajsehZdtPZC9WscTxJOI4xGTeBJtgaaHPWi0RCvnq6r6C.STJO', 'docente@sigelab.edu.gt', @rolDocente, 'ACTIVO'),
('Jorge', 'López', 'inactivo',
 '$2a$10$qyIuI6QA7hLq6cjjwfK5GevgourATNb1.OcuBHR5RxNyKjsF0FQ/C', 'inactivo@sigelab.edu.gt', @rolEncargado, 'INACTIVO');

DECLARE @admin INT = (SELECT id_usuario FROM Usuario WHERE usuario = 'admin');
DECLARE @encargado INT = (SELECT id_usuario FROM Usuario WHERE usuario = 'encargado');

INSERT INTO Laboratorio (codigo, nombre, ubicacion, capacidad, estado) VALUES
('LAB-01', 'Laboratorio de Cómputo 1', 'Edificio A, nivel 2', 30, 'ACTIVO'),
('LAB-02', 'Laboratorio de Redes', 'Edificio A, nivel 3', 20, 'ACTIVO'),
('LAB-03', 'Laboratorio de Electrónica', 'Edificio B, nivel 1', 25, 'ACTIVO');

DECLARE @lab1 INT = (SELECT id_laboratorio FROM Laboratorio WHERE codigo = 'LAB-01');
DECLARE @lab2 INT = (SELECT id_laboratorio FROM Laboratorio WHERE codigo = 'LAB-02');
DECLARE @lab3 INT = (SELECT id_laboratorio FROM Laboratorio WHERE codigo = 'LAB-03');

INSERT INTO TipoEquipo (nombre, descripcion) VALUES
('Laptop', 'Computadora portátil'),
('Cámara', 'Cámara fotográfica o de video'),
('Proyector', 'Proyector multimedia'),
('Router', 'Equipo de enrutamiento de red'),
('Switch', 'Conmutador de red'),
('Tablet', 'Tableta electrónica'),
('Monitor', 'Monitor o pantalla externa'),
('Kit electrónico', 'Kit de prácticas de electrónica');

DECLARE @tLaptop INT = (SELECT id_tipo_equipo FROM TipoEquipo WHERE nombre = 'Laptop');
DECLARE @tCamara INT = (SELECT id_tipo_equipo FROM TipoEquipo WHERE nombre = 'Cámara');
DECLARE @tProyector INT = (SELECT id_tipo_equipo FROM TipoEquipo WHERE nombre = 'Proyector');
DECLARE @tRouter INT = (SELECT id_tipo_equipo FROM TipoEquipo WHERE nombre = 'Router');
DECLARE @tSwitch INT = (SELECT id_tipo_equipo FROM TipoEquipo WHERE nombre = 'Switch');
DECLARE @tTablet INT = (SELECT id_tipo_equipo FROM TipoEquipo WHERE nombre = 'Tablet');
DECLARE @tMonitor INT = (SELECT id_tipo_equipo FROM TipoEquipo WHERE nombre = 'Monitor');
DECLARE @tKit INT = (SELECT id_tipo_equipo FROM TipoEquipo WHERE nombre = 'Kit electrónico');

INSERT INTO Equipo (codigo_inventario, nombre, id_tipo_equipo, marca, modelo, numero_serie, id_laboratorio, estado, observaciones) VALUES
('CAM-001', 'Cámara réflex', @tCamara, 'Canon', 'EOS Rebel T7', 'CN-T7-0001', @lab1, 'DISPONIBLE', NULL),
('CAM-002', 'Cámara de video', @tCamara, 'Sony', 'HDR-CX405', 'SN-CX405-0002', @lab1, 'DISPONIBLE', NULL),
('LAP-001', 'Laptop de desarrollo', @tLaptop, 'Dell', 'Latitude 5440', 'DL-5440-0001', @lab1, 'DISPONIBLE', NULL),
('LAP-002', 'Laptop de desarrollo', @tLaptop, 'HP', 'ProBook 440 G10', 'HP-440-0002', @lab1, 'PRESTADO', NULL),
('LAP-003', 'Laptop de diseño', @tLaptop, 'Lenovo', 'ThinkPad E14', 'LN-E14-0003', @lab1, 'PRESTADO', NULL),
('PRO-001', 'Proyector de aula', @tProyector, 'Epson', 'PowerLite E20', 'EP-E20-0001', @lab1, 'PRESTADO', NULL),
('PRO-002', 'Proyector portátil', @tProyector, 'BenQ', 'MS560', 'BQ-MS560-0002', @lab3, 'DISPONIBLE', NULL),
('RTR-001', 'Router de prácticas', @tRouter, 'Cisco', 'ISR 1100', 'CS-1100-0001', @lab2, 'DISPONIBLE', 'Reparado: se cambió la fuente de poder'),
('SWI-001', 'Switch de 24 puertos', @tSwitch, 'Cisco', 'Catalyst 2960', 'CS-2960-0001', @lab2, 'DISPONIBLE', NULL),
('SWI-002', 'Switch de 24 puertos', @tSwitch, 'TP-Link', 'TL-SG1024', 'TP-1024-0002', @lab2, 'BAJA', 'Placa principal quemada'),
('TAB-001', 'Tablet', @tTablet, 'Samsung', 'Galaxy Tab A9', 'SM-A9-0001', @lab3, 'DANADO', 'Pantalla quebrada'),
('TAB-002', 'Tablet', @tTablet, 'Samsung', 'Galaxy Tab A9', 'SM-A9-0002', @lab3, 'DISPONIBLE', NULL),
('MON-001', 'Monitor 24 pulgadas', @tMonitor, 'LG', '24MP400', 'LG-24-0001', @lab1, 'MANTENIMIENTO', NULL),
('KIT-001', 'Kit Arduino', @tKit, 'Arduino', 'Starter Kit UNO R3', 'AR-UNO-0001', @lab3, 'DISPONIBLE', NULL),
('KIT-002', 'Kit Arduino', @tKit, 'Arduino', 'Starter Kit UNO R3', 'AR-UNO-0002', @lab3, 'DISPONIBLE', NULL);

INSERT INTO Prestamo (solicitante_nombre, solicitante_identificacion, fecha_prestamo, fecha_esperada_devolucion, id_usuario_registra, estado, observaciones)
VALUES ('María García', '0901-24-1001', DATEADD(HOUR, -2, @ahora), DATEADD(DAY, 1, @ahora), @encargado, 'ACTIVO', 'Clase de presentación de proyectos');
DECLARE @p1 INT = SCOPE_IDENTITY();

INSERT INTO DetallePrestamo (id_prestamo, id_equipo, estado)
SELECT @p1, id_equipo, 'PENDIENTE' FROM Equipo WHERE codigo_inventario IN ('LAP-002', 'PRO-001');

INSERT INTO Prestamo (solicitante_nombre, solicitante_identificacion, fecha_prestamo, fecha_esperada_devolucion, id_usuario_registra, estado, observaciones)
VALUES ('Pedro Ramírez', '0901-23-2002', DATEADD(DAY, -5, @ahora), DATEADD(DAY, -2, @ahora), @encargado, 'ACTIVO', 'Proyecto de curso de electrónica');
DECLARE @p2 INT = SCOPE_IDENTITY();

INSERT INTO DetallePrestamo (id_prestamo, id_equipo, estado)
SELECT @p2, id_equipo, CASE WHEN codigo_inventario = 'KIT-002' THEN 'DEVUELTO' ELSE 'PENDIENTE' END
FROM Equipo WHERE codigo_inventario IN ('LAP-003', 'KIT-002');

INSERT INTO Devolucion (id_detalle_prestamo, fecha_devolucion, condicion, observaciones, id_usuario_registra)
SELECT d.id_detalle_prestamo, DATEADD(DAY, -3, @ahora), 'BUENO', 'Completo', @encargado
FROM DetallePrestamo d JOIN Equipo e ON e.id_equipo = d.id_equipo
WHERE d.id_prestamo = @p2 AND e.codigo_inventario = 'KIT-002';

INSERT INTO Prestamo (solicitante_nombre, solicitante_identificacion, fecha_prestamo, fecha_esperada_devolucion, id_usuario_registra, estado, observaciones)
VALUES ('Sofía Castillo', '0901-22-3003', DATEADD(DAY, -10, @ahora), DATEADD(DAY, -8, @ahora), @admin, 'FINALIZADO', NULL);
DECLARE @p3 INT = SCOPE_IDENTITY();

INSERT INTO DetallePrestamo (id_prestamo, id_equipo, estado)
SELECT @p3, id_equipo, 'DEVUELTO' FROM Equipo WHERE codigo_inventario IN ('TAB-001', 'TAB-002');

INSERT INTO Devolucion (id_detalle_prestamo, fecha_devolucion, condicion, observaciones, id_usuario_registra)
SELECT d.id_detalle_prestamo, DATEADD(DAY, -8, @ahora),
       CASE WHEN e.codigo_inventario = 'TAB-001' THEN 'DANADO' ELSE 'BUENO' END,
       CASE WHEN e.codigo_inventario = 'TAB-001' THEN 'Pantalla quebrada' ELSE NULL END,
       @admin
FROM DetallePrestamo d JOIN Equipo e ON e.id_equipo = d.id_equipo
WHERE d.id_prestamo = @p3;

INSERT INTO Mantenimiento (id_equipo, fecha_ingreso, tipo, descripcion, responsable, fecha_salida, resultado, estado, observaciones, id_usuario_registra)
SELECT id_equipo, DATEADD(DAY, -20, @ahora), 'CORRECTIVO', 'No enciende', 'Técnico Roberto Díaz',
       DATEADD(DAY, -15, @ahora), 'REPARADO', 'FINALIZADO', 'Se cambió la fuente de poder', @encargado
FROM Equipo WHERE codigo_inventario = 'RTR-001';

INSERT INTO Mantenimiento (id_equipo, fecha_ingreso, tipo, descripcion, responsable, fecha_salida, resultado, estado, observaciones, id_usuario_registra)
SELECT id_equipo, DATEADD(DAY, -14, @ahora), 'CORRECTIVO', 'Puertos sin enlace', 'Técnico Roberto Díaz',
       DATEADD(DAY, -12, @ahora), 'NO_REPARABLE', 'FINALIZADO', 'Placa principal quemada', @encargado
FROM Equipo WHERE codigo_inventario = 'SWI-002';

INSERT INTO Mantenimiento (id_equipo, fecha_ingreso, tipo, descripcion, responsable, estado, id_usuario_registra)
SELECT id_equipo, DATEADD(DAY, -1, @ahora), 'PREVENTIVO', 'Limpieza y calibración de color', 'Técnico Roberto Díaz',
       'EN_PROCESO', @encargado
FROM Equipo WHERE codigo_inventario = 'MON-001';

INSERT INTO Auditoria (id_usuario, fecha_hora, accion, entidad, id_registro, descripcion) VALUES
(@encargado, DATEADD(DAY, -20, @ahora), 'EQUIPO_ENVIADO_MANTENIMIENTO', 'Equipo', (SELECT id_equipo FROM Equipo WHERE codigo_inventario = 'RTR-001'), 'RTR-001 enviado a mantenimiento CORRECTIVO'),
(@encargado, DATEADD(DAY, -15, @ahora), 'MANTENIMIENTO_FINALIZADO', 'Equipo', (SELECT id_equipo FROM Equipo WHERE codigo_inventario = 'RTR-001'), 'RTR-001 REPARADO, pasa a DISPONIBLE'),
(@encargado, DATEADD(DAY, -14, @ahora), 'EQUIPO_ENVIADO_MANTENIMIENTO', 'Equipo', (SELECT id_equipo FROM Equipo WHERE codigo_inventario = 'SWI-002'), 'SWI-002 enviado a mantenimiento CORRECTIVO'),
(@encargado, DATEADD(DAY, -12, @ahora), 'MANTENIMIENTO_FINALIZADO', 'Equipo', (SELECT id_equipo FROM Equipo WHERE codigo_inventario = 'SWI-002'), 'SWI-002 NO_REPARABLE, pasa a BAJA'),
(@admin, DATEADD(DAY, -10, @ahora), 'PRESTAMO_CREADO', 'Prestamo', @p3, 'Préstamo con TAB-001, TAB-002'),
(@admin, DATEADD(DAY, -8, @ahora), 'EQUIPO_DEVUELTO', 'Prestamo', @p3, 'TAB-001 devuelto DANADO'),
(@admin, DATEADD(DAY, -8, @ahora), 'EQUIPO_DEVUELTO', 'Prestamo', @p3, 'TAB-002 devuelto BUENO'),
(@admin, DATEADD(DAY, -8, @ahora), 'PRESTAMO_FINALIZADO', 'Prestamo', @p3, 'Todos los equipos devueltos'),
(@encargado, DATEADD(DAY, -5, @ahora), 'PRESTAMO_CREADO', 'Prestamo', @p2, 'Préstamo con LAP-003, KIT-002'),
(@encargado, DATEADD(DAY, -3, @ahora), 'EQUIPO_DEVUELTO', 'Prestamo', @p2, 'KIT-002 devuelto BUENO'),
(@encargado, DATEADD(DAY, -1, @ahora), 'EQUIPO_ENVIADO_MANTENIMIENTO', 'Equipo', (SELECT id_equipo FROM Equipo WHERE codigo_inventario = 'MON-001'), 'MON-001 enviado a mantenimiento PREVENTIVO'),
(@encargado, DATEADD(HOUR, -2, @ahora), 'PRESTAMO_CREADO', 'Prestamo', @p1, 'Préstamo con LAP-002, PRO-001');

COMMIT TRANSACTION;
GO

PRINT 'Datos de SIGELAB cargados.';
GO
