package proyecto_sigelab.repository;

import java.sql.*;
import java.time.*;
import java.util.*;
import proyecto_sigelab.config.ConnectionFactory;
import proyecto_sigelab.exception.*;
import proyecto_sigelab.model.*;

/** Todo SQL reside aquí. Los métodos reciben la conexión de la misma transacción. */
public class SqlRepository {
  private final ConnectionFactory factory;

  public SqlRepository(ConnectionFactory factory) {
    this.factory = factory;
  }

  @FunctionalInterface
  public interface Work<T> {
    T run(Connection c) throws SQLException;
  }

  public <T> T transaction(Work<T> work) {
    try (Connection c = factory.open()) {
      c.setAutoCommit(false);
      try {
        T result = work.run(c);
        c.commit();
        return result;
      } catch (SQLException | RuntimeException e) {
        try {
          c.rollback();
        } catch (SQLException rollback) {
          e.addSuppressed(rollback);
        }
        throw e;
      }
    } catch (SQLException e) {
      throw new DataAccessException(e);
    }
  }

  private PreparedStatement prepare(Connection c, String sql, Object... args) throws SQLException {
    PreparedStatement p = c.prepareStatement(sql);
    p.setQueryTimeout(20);
    for (int i = 0; i < args.length; i++)
      p.setObject(i + 1, args[i] instanceof LocalDateTime d ? Timestamp.valueOf(d) : args[i]);
    return p;
  }

  public Tabla query(Connection c, String sql, Object... args) throws SQLException {
    try (var p = prepare(c, sql, args);
        var rs = p.executeQuery()) {
      List<String> headers = new ArrayList<>();
      for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
        headers.add(rs.getMetaData().getColumnLabel(i));
      List<List<Object>> rows = new ArrayList<>();
      while (rs.next()) {
        List<Object> row = new ArrayList<>();
        for (int i = 1; i <= headers.size(); i++) row.add(rs.getObject(i));
        rows.add(row);
      }
      return new Tabla(headers, rows);
    }
  }

  private int update(Connection c, String sql, Object... args) throws SQLException {
    try (var p = prepare(c, sql, args)) {
      return p.executeUpdate();
    }
  }

  private int insert(Connection c, String sql, Object... args) throws SQLException {
    try (var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      p.setQueryTimeout(20);
      for (int i = 0; i < args.length; i++)
        p.setObject(i + 1, args[i] instanceof LocalDateTime d ? Timestamp.valueOf(d) : args[i]);
      p.executeUpdate();
      try (var rs = p.getGeneratedKeys()) {
        if (!rs.next()) throw new SQLException("Sin ID generado");
        return rs.getInt(1);
      }
    }
  }

  private Usuario usuario(Tabla t) {
    if (t.filas().isEmpty()) return null;
    var a = t.filas().get(0);
    return new Usuario(
        num(a.get(0)),
        str(a.get(1)),
        str(a.get(2)),
        str(a.get(3)),
        str(a.get(4)),
        str(a.get(5)),
        str(a.get(6)),
        str(a.get(7)));
  }

  private static int num(Object o) {
    return ((Number) o).intValue();
  }

  private static String str(Object o) {
    return o == null ? "" : o.toString();
  }

  private static final String USER_SQL =
      "SELECT u.Id,u.Nombre,u.Apellido,u.NombreUsuario,u.PasswordHash,u.Correo,r.Nombre AS"
          + " Rol,u.Estado FROM dbo.Usuario u JOIN dbo.Rol r ON r.Id=u.RolId ";

  public Usuario user(Connection c, String name) throws SQLException {
    return usuario(query(c, USER_SQL + "WHERE u.NombreUsuario=?", name));
  }

  public Usuario user(Connection c, int id) throws SQLException {
    return usuario(query(c, USER_SQL + "WHERE u.Id=?", id));
  }

  public boolean emptyUsersLocked(Connection c) throws SQLException {
    return query(c, "SELECT Id FROM dbo.Usuario WITH (TABLOCKX,HOLDLOCK)").filas().isEmpty();
  }

  public int saveUser(
      Connection c,
      Integer id,
      String nombre,
      String apellido,
      String username,
      String hash,
      String correo,
      String rol,
      String estado)
      throws SQLException {
    if (id == null)
      return insert(
          c,
          "INSERT dbo.Usuario(Nombre,Apellido,NombreUsuario,PasswordHash,Correo,RolId,Estado)"
              + " VALUES(?,?,?,?,?,(SELECT Id FROM dbo.Rol WHERE Nombre=?),?)",
          nombre,
          apellido,
          username,
          hash,
          correo,
          rol,
          estado);
    if (update(
            c,
            "UPDATE dbo.Usuario SET"
                + " Nombre=?,Apellido=?,NombreUsuario=?,PasswordHash=?,Correo=?,RolId=(SELECT Id"
                + " FROM dbo.Rol WHERE Nombre=?),Estado=? WHERE Id=?",
            nombre,
            apellido,
            username,
            hash,
            correo,
            rol,
            estado,
            id)
        != 1) throw new BusinessException("El usuario ya no existe.");
    return id;
  }

  public void audit(
      Connection c, Integer uid, String action, String entity, Integer id, String detail)
      throws SQLException {
    update(
        c,
        "INSERT dbo.Auditoria(UsuarioId,Accion,Entidad,EntidadId,Detalle,Fecha) VALUES(?,?,?,?,?,DATEADD(HOUR,-6,SYSUTCDATETIME()))",
        uid,
        action,
        entity,
        id,
        detail);
  }

  public Tabla users(Connection c) throws SQLException {
    return query(
        c,
        "SELECT u.Id,u.Nombre,u.Apellido,u.NombreUsuario,u.Correo,r.Nombre AS Rol,u.Estado FROM"
            + " dbo.Usuario u JOIN dbo.Rol r ON r.Id=u.RolId ORDER BY u.Id");
  }

  public Tabla catalog(Connection c, Catalogo type) throws SQLException {
    return query(
        c,
        "SELECT Id,"
            + String.join(",", type.columnas)
            + (type == Catalogo.EQUIPO ? ",Estado" : "")
            + " FROM dbo."
            + type.tabla
            + " ORDER BY Id");
  }

  public int saveCatalog(Connection c, Catalogo type, Integer id, Object[] values)
      throws SQLException {
    if (id == null)
      return insert(
          c,
          "INSERT dbo."
              + type.tabla
              + "("
              + String.join(",", type.columnas)
              + ") VALUES("
              + String.join(",", Collections.nCopies(values.length, "?"))
              + ")",
          values);
    String assignments = String.join(",", Arrays.stream(type.columnas).map(x -> x + "=?").toList());
    Object[] args = Arrays.copyOf(values, values.length + 1);
    args[values.length] = id;
    if (update(c, "UPDATE dbo." + type.tabla + " SET " + assignments + " WHERE Id=?", args) != 1)
      throw new BusinessException("El registro ya no existe.");
    return id;
  }

  public boolean activeCatalog(Connection c, Catalogo type, int id) throws SQLException {
    return !query(
            c,
            "SELECT Id FROM dbo." + type.tabla + " WITH (HOLDLOCK) WHERE Id=? AND Estado='ACTIVO'",
            id)
        .filas()
        .isEmpty();
  }

  public Equipo equipment(Connection c, int id, boolean lock) throws SQLException {
    return equipo(
        query(
            c,
            "SELECT Id,CodigoInventario,Nombre,Estado FROM dbo.Equipo "
                + (lock ? "WITH (UPDLOCK,HOLDLOCK) " : "")
                + "WHERE Id=?",
            id));
  }

  public Equipo equipment(Connection c, String code) throws SQLException {
    return equipo(
        query(
            c,
            "SELECT Id,CodigoInventario,Nombre,Estado FROM dbo.Equipo WHERE CodigoInventario=?",
            code));
  }

  private Equipo equipo(Tabla t) {
    if (t.filas().isEmpty()) return null;
    var a = t.filas().get(0);
    return new Equipo(
        num(a.get(0)), str(a.get(1)), str(a.get(2)), EstadoEquipo.valueOf(str(a.get(3))));
  }

  public void equipmentState(Connection c, int id, EstadoEquipo state) throws SQLException {
    if (update(c, "UPDATE dbo.Equipo SET Estado=? WHERE Id=?", state.name(), id) != 1)
      throw new BusinessException("Equipo inexistente.");
  }

  public int createLoan(
      Connection c, String who, LocalDateTime start, LocalDateTime end, int operator, String notes)
      throws SQLException {
    return insert(
        c,
        "INSERT"
            + " dbo.Prestamo(Solicitante,FechaPrestamo,FechaEsperada,UsuarioRegistraId,Observaciones)"
            + " VALUES(?,?,?,?,?)",
        who,
        start,
        end,
        operator,
        notes);
  }

  public void loanItem(Connection c, int loan, int eq) throws SQLException {
    update(c, "INSERT dbo.DetallePrestamo(PrestamoId,EquipoId) VALUES(?,?)", loan, eq);
  }

  public Prestamo loanLocked(Connection c, int id) throws SQLException {
    var t =
        query(
            c,
            "SELECT Id,Solicitante,FechaEsperada,Estado FROM dbo.Prestamo WITH (UPDLOCK,HOLDLOCK)"
                + " WHERE Id=?",
            id);
    if (t.filas().isEmpty()) return null;
    var a = t.filas().get(0);
    return new Prestamo(
        num(a.get(0)), str(a.get(1)), ((Timestamp) a.get(2)).toLocalDateTime(), str(a.get(3)));
  }

  public DetallePrestamo itemLocked(Connection c, int id) throws SQLException {
    var t =
        query(
            c,
            "SELECT Id,PrestamoId,EquipoId,FechaDevolucion FROM dbo.DetallePrestamo WITH"
                + " (UPDLOCK,HOLDLOCK) WHERE Id=?",
            id);
    if (t.filas().isEmpty()) return null;
    var a = t.filas().get(0);
    return new DetallePrestamo(num(a.get(0)), num(a.get(1)), num(a.get(2)), a.get(3) != null);
  }

  public void returnItem(
      Connection c, int detail, String condition, String note, int user, LocalDateTime when)
      throws SQLException {
    update(
        c,
        "INSERT dbo.Devolucion(DetallePrestamoId,Fecha,Condicion,Observaciones,UsuarioRegistraId)"
            + " VALUES(?,?,?,?,?)",
        detail,
        when,
        condition,
        note,
        user);
    if (update(
            c,
            "UPDATE dbo.DetallePrestamo SET FechaDevolucion=? WHERE Id=? AND FechaDevolucion IS"
                + " NULL",
            when,
            detail)
        != 1) throw new BusinessException("Este equipo ya fue devuelto.");
  }

  public void closeLoanIfComplete(Connection c, int id) throws SQLException {
    update(
        c,
        "UPDATE dbo.Prestamo SET Estado='FINALIZADO' WHERE Id=? AND NOT EXISTS(SELECT 1 FROM"
            + " dbo.DetallePrestamo WHERE PrestamoId=? AND FechaDevolucion IS NULL)",
        id,
        id);
  }

  public Tabla details(Connection c, int id) throws SQLException {
    return query(
        c,
        "SELECT d.Id,e.CodigoInventario,e.Nombre,d.FechaDevolucion,v.Condicion,v.Observaciones FROM"
            + " dbo.DetallePrestamo d JOIN dbo.Equipo e ON e.Id=d.EquipoId LEFT JOIN dbo.Devolucion"
            + " v ON v.DetallePrestamoId=d.Id WHERE d.PrestamoId=? ORDER BY d.Id",
        id);
  }

  public int startMaintenance(
      Connection c,
      int equipment,
      String type,
      String description,
      String person,
      String notes,
      int user)
      throws SQLException {
    return insert(
        c,
        "INSERT"
            + " dbo.Mantenimiento(EquipoId,Tipo,Descripcion,Responsable,Observaciones,UsuarioRegistraId,FechaIngreso)"
            + " VALUES(?,?,?,?,?,?,DATEADD(HOUR,-6,SYSUTCDATETIME()))",
        equipment,
        type,
        description,
        person,
        notes,
        user);
  }

  public Mantenimiento maintenanceLocked(Connection c, int id) throws SQLException {
    var t =
        query(
            c,
            "SELECT Id,EquipoId,Estado FROM dbo.Mantenimiento WITH (UPDLOCK,HOLDLOCK) WHERE Id=?",
            id);
    if (t.filas().isEmpty()) return null;
    var a = t.filas().get(0);
    return new Mantenimiento(num(a.get(0)), num(a.get(1)), str(a.get(2)));
  }

  public void finishMaintenance(Connection c, int id, String result) throws SQLException {
    if (update(
            c,
            "UPDATE dbo.Mantenimiento SET"
                + " Estado='FINALIZADO',Resultado=?,FechaSalida=DATEADD(HOUR,-6,SYSUTCDATETIME())"
                + " WHERE Id=? AND Estado='ABIERTO'",
            result,
            id)
        != 1) throw new BusinessException("Mantenimiento ya cerrado.");
  }

  public Tabla maintenance(Connection c) throws SQLException {
    return query(
        c,
        "SELECT"
            + " m.Id,e.CodigoInventario,m.Tipo,m.Descripcion,m.Responsable,m.FechaIngreso,m.FechaSalida,m.Resultado,m.Estado,m.Observaciones"
            + " FROM dbo.Mantenimiento m JOIN dbo.Equipo e ON e.Id=m.EquipoId ORDER BY m.Id DESC");
  }

  public Tabla auditList(Connection c) throws SQLException {
    return query(
        c,
        "SELECT TOP (1000) a.Id,a.Fecha,u.NombreUsuario,a.Accion,a.Entidad,a.EntidadId,a.Detalle"
            + " FROM dbo.Auditoria a LEFT JOIN dbo.Usuario u ON u.Id=a.UsuarioId ORDER BY a.Id"
            + " DESC");
  }

  public Tabla dashboard(Connection c) throws SQLException {
    return query(
        c,
        """
SELECT 'Equipos total' AS Indicador,COUNT(*) AS Cantidad FROM dbo.Equipo
UNION ALL SELECT 'Equipos '+s.Estado,COUNT(e.Id) FROM (VALUES ('DISPONIBLE'),('PRESTADO'),('DANADO'),('MANTENIMIENTO'),('BAJA')) s(Estado) LEFT JOIN dbo.Equipo e ON e.Estado=s.Estado GROUP BY s.Estado
UNION ALL SELECT 'Préstamos activos',COUNT(*) FROM dbo.Prestamo WHERE Estado='ACTIVO'
UNION ALL SELECT 'Préstamos vencidos',COUNT(*) FROM dbo.Prestamo p WHERE FechaEsperada<DATEADD(HOUR,-6,SYSUTCDATETIME()) AND EXISTS(SELECT 1 FROM dbo.DetallePrestamo d WHERE d.PrestamoId=p.Id AND d.FechaDevolucion IS NULL)
""");
  }

  public Tabla loans(Connection c, String filter, LocalDate from, LocalDate to)
      throws SQLException {
    String where =
        switch (filter) {
          case "ACTIVO" -> "p.Estado='ACTIVO'";
          case "FINALIZADO" -> "p.Estado='FINALIZADO'";
          case "VENCIDO" ->
              "p.FechaEsperada<DATEADD(HOUR,-6,SYSUTCDATETIME()) AND EXISTS(SELECT 1 FROM"
                  + " dbo.DetallePrestamo d WHERE d.PrestamoId=p.Id AND d.FechaDevolucion IS NULL)";
          default -> "1=1";
        };
    return query(
        c,
        "SELECT p.Id,p.Solicitante,p.FechaPrestamo,p.FechaEsperada,p.Estado,u.NombreUsuario AS"
            + " RegistradoPor,(SELECT COUNT(*) FROM dbo.DetallePrestamo d WHERE d.PrestamoId=p.Id"
            + " AND d.FechaDevolucion IS NULL) AS Pendientes FROM dbo.Prestamo p JOIN dbo.Usuario u"
            + " ON u.Id=p.UsuarioRegistraId WHERE "
            + where
            + " AND CAST(p.FechaPrestamo AS DATE) BETWEEN ? AND ? ORDER BY p.Id DESC",
        java.sql.Date.valueOf(from),
        java.sql.Date.valueOf(to));
  }

  public Tabla equipmentReport(Connection c, String state) throws SQLException {
    return query(
        c,
        "SELECT e.Id,e.CodigoInventario,e.Nombre,t.Nombre AS Tipo,l.Nombre AS Laboratorio,e.Estado"
            + " FROM dbo.Equipo e JOIN dbo.TipoEquipo t ON t.Id=e.TipoEquipoId JOIN dbo.Laboratorio"
            + " l ON l.Id=e.LaboratorioId WHERE (?='TODOS' OR e.Estado=?) ORDER BY"
            + " e.CodigoInventario",
        state,
        state);
  }

  public Tabla equipmentHistory(Connection c, int id) throws SQLException {
    return query(
        c,
        "SELECT p.Id AS"
            + " Prestamo,p.Solicitante,p.FechaPrestamo,p.FechaEsperada,d.FechaDevolucion,v.Condicion,v.Observaciones"
            + " FROM dbo.DetallePrestamo d JOIN dbo.Prestamo p ON p.Id=d.PrestamoId LEFT JOIN"
            + " dbo.Devolucion v ON v.DetallePrestamoId=d.Id WHERE d.EquipoId=? ORDER BY"
            + " p.FechaPrestamo DESC",
        id);
  }

  public Tabla maintenanceHistory(Connection c, int id) throws SQLException {
    return query(
        c,
        "SELECT"
            + " Id,FechaIngreso,Tipo,Descripcion,Responsable,FechaSalida,Resultado,Estado,Observaciones"
            + " FROM dbo.Mantenimiento WHERE EquipoId=? ORDER BY FechaIngreso DESC",
        id);
  }
}
