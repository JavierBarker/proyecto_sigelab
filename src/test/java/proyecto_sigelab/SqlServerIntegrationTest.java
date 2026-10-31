package proyecto_sigelab;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import proyecto_sigelab.config.*;
import proyecto_sigelab.exception.*;
import proyecto_sigelab.model.*;
import proyecto_sigelab.repository.SqlRepository;
import proyecto_sigelab.service.InventoryService;

/** Ejecutar sobre una base de pruebas con el esquema instalado. Limpia solo sus registros. */
@EnabledIfEnvironmentVariable(named = "SIGELAB_INTEGRATION", matches = "1")
class SqlServerIntegrationTest {
  private int insert(Connection c, String sql, Object... args) throws SQLException {
    try (var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
      p.executeUpdate();
      try (var r = p.getGeneratedKeys()) {
        r.next();
        return r.getInt(1);
      }
    }
  }

  @Test
  void sqlConstraintsConcurrencyAndLifecycle() throws Exception {
    var factory = new ConnectionFactory(new AppConfig());
    var db = new SqlRepository(factory);
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    int user = 0, lab = 0, type = 0;
    List<Integer> equipment = new ArrayList<>();
    try (Connection c = factory.open()) {
      try {
        user =
            insert(
                c,
                "INSERT dbo.Usuario(Nombre,Apellido,NombreUsuario,PasswordHash,Correo,RolId,Estado)"
                    + " VALUES('Prueba','Automática',?,'NO_LOGIN','test@example.invalid',(SELECT Id"
                    + " FROM dbo.Rol WHERE Nombre='ADMIN'),'ACTIVO')",
                "test_" + suffix);
        lab =
            insert(
                c,
                "INSERT dbo.Laboratorio(Codigo,Nombre,Ubicacion,Capacidad)"
                    + " VALUES(?,'Prueba','Temporal',3)",
                "TEST-" + suffix);
        type = insert(c, "INSERT dbo.TipoEquipo(Nombre) VALUES(?)", "Test-" + suffix);
        for (int i = 1; i <= 3; i++)
          equipment.add(
              insert(
                  c,
                  "INSERT"
                      + " dbo.Equipo(CodigoInventario,Nombre,TipoEquipoId,Marca,Modelo,LaboratorioId)"
                      + " VALUES(?,'Prueba',?,'Demo','Demo',?)",
                  "TEST-" + suffix + "-" + i,
                  type,
                  lab));
        final int typeId = type, labId = lab;
        assertThrows(
            SQLException.class,
            () ->
                insert(
                    c,
                    "INSERT"
                        + " dbo.Equipo(CodigoInventario,Nombre,TipoEquipoId,Marca,Modelo,LaboratorioId)"
                        + " VALUES(?,'Duplicado',?,'D','D',?)",
                    "TEST-" + suffix + "-1",
                    typeId,
                    labId));
        InventoryService service = new InventoryService(db, new Session(user, "Prueba", "ADMIN"));
        LocalDateTime due = LocalDateTime.now(ZoneId.of("America/Guatemala")).plusDays(1);
        int loan = service.loan("Prueba automática", equipment, due, "");
        assertThrows(
            BusinessException.class,
            () -> service.loan("Duplicado", List.of(equipment.get(0)), due, ""));
        Tabla details = service.details(loan);
        List<Integer> ids =
            details.filas().stream().map(a -> ((Number) a.get(0)).intValue()).toList();
        service.returnEquipment(loan, ids.subList(0, 2), "BUENO", "");
        assertEquals("ACTIVO", db.transaction(x -> db.loanLocked(x, loan)).estado());
        assertThrows(
            BusinessException.class,
            () -> service.returnEquipment(loan, List.of(ids.get(0)), "BUENO", ""));
        service.returnEquipment(loan, List.of(ids.get(2)), "DANADO", "");
        assertEquals("FINALIZADO", db.transaction(x -> db.loanLocked(x, loan)).estado());
        int last = equipment.get(2);
        int m = service.startMaintenance(last, "CORRECTIVO", "Prueba", "Técnico", "");
        service.finishMaintenance(m, "REPARADO");
        assertEquals(
            EstadoEquipo.DISPONIBLE, db.transaction(x -> db.equipment(x, last, false)).estado());
        // Dos conexiones independientes compiten por el mismo equipo.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        try {
          Callable<Boolean> task =
              () -> {
                gate.await();
                try {
                  service.loan("Concurrente", List.of(last), due, "");
                  return true;
                } catch (BusinessException | DataAccessException e) {
                  return false;
                }
              };
          Future<Boolean> a = pool.submit(task), b = pool.submit(task);
          gate.countDown();
          int successes =
              (a.get(45, TimeUnit.SECONDS) ? 1 : 0) + (b.get(45, TimeUnit.SECONDS) ? 1 : 0);
          assertEquals(1, successes);
        } finally {
          pool.shutdownNow();
        }
      } finally {
        if (user > 0) {
          try (var p = c.prepareStatement("DELETE dbo.Auditoria WHERE UsuarioId=?")) {
            p.setInt(1, user);
            p.executeUpdate();
          }
          try (var p = c.prepareStatement("DELETE dbo.Devolucion WHERE UsuarioRegistraId=?")) {
            p.setInt(1, user);
            p.executeUpdate();
          }
          try (var p =
              c.prepareStatement(
                  "DELETE dbo.DetallePrestamo WHERE PrestamoId IN (SELECT Id FROM dbo.Prestamo"
                      + " WHERE UsuarioRegistraId=?)")) {
            p.setInt(1, user);
            p.executeUpdate();
          }
          try (var p = c.prepareStatement("DELETE dbo.Prestamo WHERE UsuarioRegistraId=?")) {
            p.setInt(1, user);
            p.executeUpdate();
          }
          try (var p = c.prepareStatement("DELETE dbo.Mantenimiento WHERE UsuarioRegistraId=?")) {
            p.setInt(1, user);
            p.executeUpdate();
          }
        }
        for (int id : equipment)
          try (var p = c.prepareStatement("DELETE dbo.Equipo WHERE Id=?")) {
            p.setInt(1, id);
            p.executeUpdate();
          }
        if (lab > 0)
          try (var p = c.prepareStatement("DELETE dbo.Laboratorio WHERE Id=?")) {
            p.setInt(1, lab);
            p.executeUpdate();
          }
        if (type > 0)
          try (var p = c.prepareStatement("DELETE dbo.TipoEquipo WHERE Id=?")) {
            p.setInt(1, type);
            p.executeUpdate();
          }
        if (user > 0)
          try (var p = c.prepareStatement("DELETE dbo.Usuario WHERE Id=?")) {
            p.setInt(1, user);
            p.executeUpdate();
          }
      }
    }
  }
}
