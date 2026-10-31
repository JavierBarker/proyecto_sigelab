USE SIGELAB;
GO
SELECT t.name AS Tabla FROM sys.tables t WHERE t.schema_id=SCHEMA_ID('dbo') ORDER BY t.name;
SELECT e.CodigoInventario,e.Nombre,t.Nombre AS Tipo,l.Codigo AS Laboratorio,e.Estado
FROM dbo.Equipo e JOIN dbo.TipoEquipo t ON e.TipoEquipoId=t.Id
JOIN dbo.Laboratorio l ON e.LaboratorioId=l.Id ORDER BY e.CodigoInventario;
SELECT Estado,COUNT(*) AS Cantidad FROM dbo.Equipo GROUP BY Estado;
GO
