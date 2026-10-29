package proyecto_sigelab;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import proyecto_sigelab.exception.BusinessException;
import proyecto_sigelab.model.*;
import proyecto_sigelab.repository.SqlRepository;
import proyecto_sigelab.service.*;

/** Pruebas de servicios con repositorio en memoria: NO sustituyen SQL Server real. */
class WorkflowTest {
  static class Memory extends SqlRepository {
    Map<Integer, Equipo> equipment = new HashMap<>();
    Map<Integer, Prestamo> loans = new HashMap<>();
    Map<Integer, DetallePrestamo> details = new HashMap<>();
    Map<Integer, Mantenimiento> maintenance = new HashMap<>();
    List<String> audits = new ArrayList<>();
    boolean failAudit;

    Memory() {
      super(null);
      for (int i = 1; i <= 3; i++)
        equipment.put(i, new Equipo(i, "EQ-" + i, "Equipo", EstadoEquipo.DISPONIBLE));
    }

    @Override
    public <T> T transaction(Work<T> work) {
      var e = new HashMap<>(equipment);
      var l = new HashMap<>(loans);
      var d = new HashMap<>(details);
      var m = new HashMap<>(maintenance);
      var a = new ArrayList<>(audits);
      try {
        return work.run(null);
      } catch (SQLException | RuntimeException ex) {
        equipment = e;
        loans = l;
        details = d;
        maintenance = m;
        audits = a;
        throw ex instanceof RuntimeException re ? re : new RuntimeException(ex);
      }
    }

    @Override
    public Usuario user(Connection c, int id) {
      return new Usuario(id, "Admin", "Demo", "admin", "h", "a@b.c", "ADMIN", "ACTIVO");
    }

    @Override
    public Equipo equipment(Connection c, int id, boolean lock) {
      return equipment.get(id);
    }

    @Override
    public Equipo equipment(Connection c, String code) {
      return equipment.values().stream()
          .filter(e -> e.codigo().equals(code))
          .findFirst()
          .orElse(null);
    }

    @Override
    public void equipmentState(Connection c, int id, EstadoEquipo s) {
      Equipo e = equipment.get(id);
      equipment.put(id, new Equipo(id, e.codigo(), e.nombre(), s));
    }

    @Override
    public int createLoan(
        Connection c, String who, LocalDateTime start, LocalDateTime end, int user, String notes) {
      int id = loans.size() + 1;
      loans.put(id, new Prestamo(id, who, end, "ACTIVO"));
      return id;
    }

    @Override
    public void loanItem(Connection c, int loan, int eq) {
      int id = details.size() + 1;
      details.put(id, new DetallePrestamo(id, loan, eq, false));
    }

    @Override
    public Prestamo loanLocked(Connection c, int id) {
      return loans.get(id);
    }

    @Override
    public DetallePrestamo itemLocked(Connection c, int id) {
      return details.get(id);
    }

    @Override
    public void returnItem(
        Connection c, int id, String condition, String note, int user, LocalDateTime when) {
      var d = details.get(id);
      details.put(id, new DetallePrestamo(id, d.prestamoId(), d.equipoId(), true));
    }

    @Override
    public void closeLoanIfComplete(Connection c, int id) {
      if (details.values().stream().noneMatch(d -> d.prestamoId() == id && !d.devuelto())) {
        var p = loans.get(id);
        loans.put(id, new Prestamo(id, p.solicitante(), p.fechaEsperada(), "FINALIZADO"));
      }
    }

    @Override
    public int startMaintenance(
        Connection c, int eq, String type, String desc, String person, String notes, int user) {
      int id = maintenance.size() + 1;
      maintenance.put(id, new Mantenimiento(id, eq, "ABIERTO"));
      return id;
    }

    @Override
    public Mantenimiento maintenanceLocked(Connection c, int id) {
      return maintenance.get(id);
    }

    @Override
    public void finishMaintenance(Connection c, int id, String result) {
      var m = maintenance.get(id);
      maintenance.put(id, new Mantenimiento(id, m.equipoId(), "FINALIZADO"));
    }

    @Override
    public void audit(
        Connection c, Integer uid, String action, String entity, Integer id, String detail) {
      if (failAudit) throw new BusinessException("Fallo de auditoría simulado");
      audits.add(action);
    }
  }

  InventoryService service(Memory db) {
    return new InventoryService(db, new Session(1, "Admin", "ADMIN"));
  }

  @Test
  void completeEquipmentLifecycle() {
    Memory db = new Memory();
    var s = service(db);
    int p = s.loan("Alumno", List.of(1), LocalDateTime.now().plusDays(1), "");
    assertEquals(EstadoEquipo.PRESTADO, db.equipment.get(1).estado());
    assertThrows(
        BusinessException.class,
        () -> s.loan("Otro", List.of(1), LocalDateTime.now().plusDays(1), ""));
    s.returnEquipment(p, List.of(1), "DANADO", "");
    assertEquals(EstadoEquipo.DANADO, db.equipment.get(1).estado());
    assertThrows(BusinessException.class, () -> s.identify("EQ-1"));
    int m = s.startMaintenance(1, "CORRECTIVO", "Reparación", "Técnico", "");
    assertEquals(EstadoEquipo.MANTENIMIENTO, db.equipment.get(1).estado());
    s.finishMaintenance(m, "REPARADO");
    assertEquals(EstadoEquipo.DISPONIBLE, db.equipment.get(1).estado());
    assertDoesNotThrow(() -> s.loan("Alumno", List.of(1), LocalDateTime.now().plusDays(1), ""));
  }

  @Test
  void partialReturnKeepsLoanActiveUntilLastItem() {
    Memory db = new Memory();
    var s = service(db);
    int p = s.loan("Alumno", List.of(1, 2, 3), LocalDateTime.now().plusDays(1), "");
    s.returnEquipment(p, List.of(1, 2), "BUENO", "");
    assertEquals("ACTIVO", db.loans.get(p).estado());
    assertEquals(EstadoEquipo.PRESTADO, db.equipment.get(3).estado());
    assertThrows(BusinessException.class, () -> s.returnEquipment(p, List.of(1), "BUENO", ""));
    s.returnEquipment(p, List.of(3), "BUENO", "");
    assertEquals("FINALIZADO", db.loans.get(p).estado());
  }

  @Test
  void nonRepairableEquipmentBecomesRetired() {
    Memory db = new Memory();
    var s = service(db);
    int m = s.startMaintenance(1, "PREVENTIVO", "Inspección", "Técnico", "");
    s.finishMaintenance(m, "NO_REPARABLE");
    assertEquals(EstadoEquipo.BAJA, db.equipment.get(1).estado());
    assertThrows(BusinessException.class, () -> s.identify("EQ-1"));
    assertThrows(BusinessException.class, () -> s.finishMaintenance(m, "REPARADO"));
  }

  @Test
  void failedAuditRollsBackWholeLoan() {
    Memory db = new Memory();
    db.failAudit = true;
    var s = service(db);
    assertThrows(
        BusinessException.class,
        () -> s.loan("Alumno", List.of(1, 2), LocalDateTime.now().plusDays(1), ""));
    assertTrue(db.loans.isEmpty());
    assertTrue(db.details.isEmpty());
    assertEquals(EstadoEquipo.DISPONIBLE, db.equipment.get(1).estado());
  }

  @Test
  void invalidSecondReturnRollsBackFirstReturn() {
    Memory db = new Memory();
    var s = service(db);
    int p = s.loan("Alumno", List.of(1, 2), LocalDateTime.now().plusDays(1), "");
    assertThrows(BusinessException.class, () -> s.returnEquipment(p, List.of(1, 999), "BUENO", ""));
    assertFalse(db.details.get(1).devuelto());
    assertEquals(EstadoEquipo.PRESTADO, db.equipment.get(1).estado());
  }
}
