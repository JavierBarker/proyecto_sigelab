USE SIGELAB;
GO
SET XACT_ABORT ON;
BEGIN TRY
BEGIN TRANSACTION;
-- Instalación inicial: no elimina ni reemplaza tablas existentes.
-- Si ya fue aplicado, no volver a ejecutar; los cambios futuros serán migraciones.
CREATE TABLE dbo.Rol (
 Id INT IDENTITY PRIMARY KEY, Nombre VARCHAR(20) NOT NULL UNIQUE,
 CONSTRAINT CK_Rol_Nombre CHECK (Nombre IN ('ADMIN','ENCARGADO','DOCENTE'))
);
CREATE TABLE dbo.Usuario (
 Id INT IDENTITY PRIMARY KEY, Nombre NVARCHAR(80) NOT NULL, Apellido NVARCHAR(80) NOT NULL,
 NombreUsuario NVARCHAR(50) NOT NULL UNIQUE, PasswordHash VARCHAR(512) NOT NULL,
 Correo NVARCHAR(254) NOT NULL, RolId INT NOT NULL REFERENCES dbo.Rol(Id),
 Estado VARCHAR(10) NOT NULL DEFAULT 'ACTIVO',
 CONSTRAINT CK_Usuario_Estado CHECK (Estado IN ('ACTIVO','INACTIVO'))
);
CREATE TABLE dbo.Laboratorio (
 Id INT IDENTITY PRIMARY KEY, Codigo NVARCHAR(30) NOT NULL UNIQUE,
 Nombre NVARCHAR(100) NOT NULL, Ubicacion NVARCHAR(150) NOT NULL,
 Capacidad INT NOT NULL CHECK (Capacidad > 0),
 Estado VARCHAR(10) NOT NULL DEFAULT 'ACTIVO' CHECK (Estado IN ('ACTIVO','INACTIVO'))
);
CREATE TABLE dbo.TipoEquipo (
 Id INT IDENTITY PRIMARY KEY, Nombre NVARCHAR(80) NOT NULL UNIQUE,
 Estado VARCHAR(10) NOT NULL DEFAULT 'ACTIVO' CHECK (Estado IN ('ACTIVO','INACTIVO'))
);
CREATE TABLE dbo.Equipo (
 Id INT IDENTITY PRIMARY KEY, CodigoInventario NVARCHAR(30) NOT NULL UNIQUE,
 Nombre NVARCHAR(100) NOT NULL, TipoEquipoId INT NOT NULL REFERENCES dbo.TipoEquipo(Id),
 Marca NVARCHAR(80) NOT NULL, Modelo NVARCHAR(80) NOT NULL, NumeroSerie NVARCHAR(100) NULL,
 LaboratorioId INT NOT NULL REFERENCES dbo.Laboratorio(Id),
 Estado VARCHAR(15) NOT NULL DEFAULT 'DISPONIBLE', Observaciones NVARCHAR(1000) NULL,
 CONSTRAINT CK_Equipo_Estado CHECK (Estado IN ('DISPONIBLE','PRESTADO','DANADO','MANTENIMIENTO','BAJA'))
);
CREATE TABLE dbo.Prestamo (
 Id INT IDENTITY PRIMARY KEY, Solicitante NVARCHAR(150) NOT NULL,
 FechaPrestamo DATETIME2 NOT NULL DEFAULT DATEADD(HOUR,-6,SYSUTCDATETIME()), FechaEsperada DATETIME2 NOT NULL,
 UsuarioRegistraId INT NOT NULL REFERENCES dbo.Usuario(Id),
 Estado VARCHAR(12) NOT NULL DEFAULT 'ACTIVO' CHECK (Estado IN ('ACTIVO','FINALIZADO')),
 Observaciones NVARCHAR(1000) NULL,
 CONSTRAINT CK_Prestamo_Fechas CHECK (FechaEsperada > FechaPrestamo)
);
CREATE TABLE dbo.DetallePrestamo (
 Id INT IDENTITY PRIMARY KEY, PrestamoId INT NOT NULL REFERENCES dbo.Prestamo(Id),
 EquipoId INT NOT NULL REFERENCES dbo.Equipo(Id),
 FechaDevolucion DATETIME2 NULL,
 CONSTRAINT UQ_Detalle_PrestamoEquipo UNIQUE (PrestamoId, EquipoId)
);
-- Segunda barrera contra dos préstamos simultáneos del mismo equipo.
CREATE UNIQUE INDEX UX_Detalle_EquipoPendiente ON dbo.DetallePrestamo(EquipoId) WHERE FechaDevolucion IS NULL;
CREATE TABLE dbo.Devolucion (
 Id INT IDENTITY PRIMARY KEY, DetallePrestamoId INT NOT NULL UNIQUE REFERENCES dbo.DetallePrestamo(Id),
 Fecha DATETIME2 NOT NULL DEFAULT DATEADD(HOUR,-6,SYSUTCDATETIME()),
 Condicion VARCHAR(10) NOT NULL CHECK (Condicion IN ('BUENO','DANADO')),
 Observaciones NVARCHAR(1000) NULL, UsuarioRegistraId INT NOT NULL REFERENCES dbo.Usuario(Id)
);
CREATE TABLE dbo.Mantenimiento (
 Id INT IDENTITY PRIMARY KEY, EquipoId INT NOT NULL REFERENCES dbo.Equipo(Id),
 FechaIngreso DATETIME2 NOT NULL DEFAULT DATEADD(HOUR,-6,SYSUTCDATETIME()),
 Tipo VARCHAR(12) NOT NULL CHECK (Tipo IN ('PREVENTIVO','CORRECTIVO')),
 Descripcion NVARCHAR(1000) NOT NULL, Responsable NVARCHAR(150) NOT NULL,
 FechaSalida DATETIME2 NULL, Resultado VARCHAR(15) NULL,
 Estado VARCHAR(12) NOT NULL DEFAULT 'ABIERTO' CHECK (Estado IN ('ABIERTO','FINALIZADO')),
 Observaciones NVARCHAR(1000) NULL, UsuarioRegistraId INT NOT NULL REFERENCES dbo.Usuario(Id),
 CONSTRAINT CK_Mantenimiento_Cierre CHECK (
   (Estado='ABIERTO' AND FechaSalida IS NULL AND Resultado IS NULL) OR
   (Estado='FINALIZADO' AND FechaSalida IS NOT NULL AND Resultado IS NOT NULL
    AND FechaSalida >= FechaIngreso AND Resultado IN ('REPARADO','NO_REPARABLE')))
);
CREATE UNIQUE INDEX UX_Mantenimiento_Abierto ON dbo.Mantenimiento(EquipoId) WHERE Estado='ABIERTO';
CREATE TABLE dbo.Auditoria (
 Id BIGINT IDENTITY PRIMARY KEY, Fecha DATETIME2 NOT NULL DEFAULT DATEADD(HOUR,-6,SYSUTCDATETIME()),
 UsuarioId INT NULL REFERENCES dbo.Usuario(Id), Accion VARCHAR(60) NOT NULL,
 Entidad VARCHAR(60) NOT NULL, EntidadId INT NULL, Detalle NVARCHAR(1000) NULL
);
CREATE INDEX IX_Equipo_Laboratorio ON dbo.Equipo(LaboratorioId);
CREATE INDEX IX_Equipo_Tipo ON dbo.Equipo(TipoEquipoId);
CREATE INDEX IX_Prestamo_Fecha ON dbo.Prestamo(FechaEsperada, Estado);
CREATE INDEX IX_Mantenimiento_Equipo ON dbo.Mantenimiento(EquipoId, FechaIngreso);
CREATE INDEX IX_Auditoria_Fecha ON dbo.Auditoria(Fecha);
COMMIT;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT > 0 ROLLBACK;
 THROW;
END CATCH;
GO
