/* Idempotent facial biometric storage for SIGELAB. */
USE SIGELAB;

IF OBJECT_ID(N'dbo.PerfilFacial', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.PerfilFacial
    (
        id_perfil_facial INT IDENTITY(1,1) NOT NULL,
        id_usuario INT NOT NULL,
        embedding VARBINARY(MAX) NOT NULL,
        modelo NVARCHAR(100) NOT NULL,
        dimension INT NOT NULL,
        fecha_registro DATETIME2(0) NOT NULL CONSTRAINT DF_PerfilFacial_FechaRegistro DEFAULT DATEADD(HOUR,-6,SYSUTCDATETIME()),
        fecha_actualizacion DATETIME2(0) NOT NULL CONSTRAINT DF_PerfilFacial_FechaActualizacion DEFAULT DATEADD(HOUR,-6,SYSUTCDATETIME()),
        activo BIT NOT NULL CONSTRAINT DF_PerfilFacial_Activo DEFAULT 1,
        cantidad_muestras INT NOT NULL,
        quality_score FLOAT NULL,
        version_algoritmo NVARCHAR(50) NOT NULL,

        CONSTRAINT PK_PerfilFacial PRIMARY KEY (id_perfil_facial),
        CONSTRAINT UQ_PerfilFacial_Usuario UNIQUE (id_usuario),
        CONSTRAINT FK_PerfilFacial_Usuario FOREIGN KEY (id_usuario)
            REFERENCES dbo.Usuario(Id),
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
        fecha_hora DATETIME2(0) NOT NULL CONSTRAINT DF_AuditoriaFacial_Fecha DEFAULT DATEADD(HOUR,-6,SYSUTCDATETIME()),
        accion VARCHAR(30) NOT NULL,
        resultado VARCHAR(20) NOT NULL,
        score FLOAT NULL,
        threshold FLOAT NULL,
        detalle NVARCHAR(1000) NULL,
        dispositivo NVARCHAR(200) NULL,

        CONSTRAINT PK_AuditoriaFacial PRIMARY KEY (id_auditoria),
        CONSTRAINT FK_AuditoriaFacial_Usuario FOREIGN KEY (id_usuario)
            REFERENCES dbo.Usuario(Id)
            ON DELETE SET NULL,
        CONSTRAINT CK_AuditoriaFacial_Accion CHECK
            (accion IN ('ENROLL_INICIADO', 'ENROLL_EXITOSO', 'ENROLL_FALLIDO',
                        'VERIFY_INICIADO', 'VERIFY_EXITOSO', 'VERIFY_FALLIDO',
                        'LIVENESS_FALLIDO', 'CAMARA_NO_DISPONIBLE', 'PERFIL_ELIMINADO',
                         'CAMARA_FRAME_FALLIDO', 'PREVIEW_FALLIDO'))
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
