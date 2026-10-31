USE SIGELAB;
GO
SET XACT_ABORT ON;
BEGIN TRY
BEGIN TRANSACTION;
IF NOT EXISTS (SELECT 1 FROM dbo.Rol WHERE Nombre='ADMIN') INSERT dbo.Rol(Nombre) VALUES ('ADMIN');
IF NOT EXISTS (SELECT 1 FROM dbo.Rol WHERE Nombre='ENCARGADO') INSERT dbo.Rol(Nombre) VALUES ('ENCARGADO');
IF NOT EXISTS (SELECT 1 FROM dbo.TipoEquipo WHERE Nombre=N'Cámara') INSERT dbo.TipoEquipo(Nombre) VALUES(N'Cámara');
IF NOT EXISTS (SELECT 1 FROM dbo.TipoEquipo WHERE Nombre=N'Proyector') INSERT dbo.TipoEquipo(Nombre) VALUES(N'Proyector');
IF NOT EXISTS (SELECT 1 FROM dbo.Laboratorio WHERE Codigo=N'LAB-001')
 INSERT dbo.Laboratorio(Codigo,Nombre,Ubicacion,Capacidad) VALUES(N'LAB-001',N'Laboratorio de Sistemas',N'Edificio principal',30);
IF NOT EXISTS (SELECT 1 FROM dbo.Equipo WHERE CodigoInventario=N'CAM-001')
 INSERT dbo.Equipo(CodigoInventario,Nombre,TipoEquipoId,Marca,Modelo,NumeroSerie,LaboratorioId)
 SELECT N'CAM-001',N'Cámara de demostración',t.Id,N'Demo',N'Académico',N'DEMO-CAM-001',l.Id
 FROM dbo.TipoEquipo t CROSS JOIN dbo.Laboratorio l WHERE t.Nombre=N'Cámara' AND l.Codigo=N'LAB-001';
IF NOT EXISTS (SELECT 1 FROM dbo.Equipo WHERE CodigoInventario=N'PRO-001')
 INSERT dbo.Equipo(CodigoInventario,Nombre,TipoEquipoId,Marca,Modelo,NumeroSerie,LaboratorioId)
 SELECT N'PRO-001',N'Proyector de demostración',t.Id,N'Demo',N'Académico',N'DEMO-PRO-001',l.Id
 FROM dbo.TipoEquipo t CROSS JOIN dbo.Laboratorio l WHERE t.Nombre=N'Proyector' AND l.Codigo=N'LAB-001';
IF NOT EXISTS (SELECT 1 FROM dbo.TipoEquipo WHERE Nombre=N'Laptop') INSERT dbo.TipoEquipo(Nombre) VALUES(N'Laptop');
IF NOT EXISTS (SELECT 1 FROM dbo.Equipo WHERE CodigoInventario=N'LAP-001')
 INSERT dbo.Equipo(CodigoInventario,Nombre,TipoEquipoId,Marca,Modelo,NumeroSerie,LaboratorioId)
 SELECT N'LAP-001',N'Laptop de demostración',t.Id,N'Demo',N'Académico',N'DEMO-LAP-001',l.Id
 FROM dbo.TipoEquipo t CROSS JOIN dbo.Laboratorio l WHERE t.Nombre=N'Laptop' AND l.Codigo=N'LAB-001';
COMMIT;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT > 0 ROLLBACK;
 THROW;
END CATCH;
GO
-- No se crean usuarios hasta implementar hash de contraseñas y registro facial.
