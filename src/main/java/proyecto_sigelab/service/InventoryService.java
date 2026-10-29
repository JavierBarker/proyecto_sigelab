package proyecto_sigelab.service;

import java.sql.*;
import java.time.*;
import java.util.*;
import proyecto_sigelab.exception.BusinessException;
import proyecto_sigelab.model.*;
import proyecto_sigelab.repository.SqlRepository;

public final class InventoryService {
  private final SqlRepository db;
  private final Session session;

  public InventoryService(SqlRepository db, Session session) {
    this.db = db;
    this.session = session;
  }

  private Usuario authorize(Connection c, boolean admin) throws SQLException {
    Usuario u = db.user(c, session.usuarioId());
    if (admin) Rules.admin(u);
    else Rules.operator(u);
    return u;
  }

  public Tabla catalog(Catalogo type) {
    return db.transaction(
        c -> {
          authorize(c, false);
          return db.catalog(c, type);
        });
  }

  public Tabla users() {
    return db.transaction(
        c -> {
          authorize(c, true);
          return db.users(c);
        });
  }

  public int save(Catalogo type, Integer id, Object[] values) {
    if (values == null || values.length != type.columnas.length)
      throw new BusinessException("Datos incompletos.");
    Object[] v = values.clone();
    for (int i = 0; i < v.length; i++) {
      String col = type.columnas[i];
      if (v[i] == null) v[i] = "";
      String text = v[i].toString().trim();
      if (col.equals("Capacidad") || col.endsWith("Id")) {
        try {
          int number = Integer.parseInt(text);
          if (number <= 0) throw new NumberFormatException();
          v[i] = number;
        } catch (NumberFormatException e) {
          throw new BusinessException(col + " debe ser un entero positivo.");
        }
      } else if (col.equals("Observaciones") || col.equals("NumeroSerie")) {
        if (text.length() > (col.equals("Observaciones") ? 1000 : 100))
          throw new BusinessException(col + " demasiado largo.");
        v[i] = text;
      } else {
        int max =
            col.equals("Codigo") || col.equals("CodigoInventario")
                ? 30
                : col.equals("Ubicacion")
                    ? 150
                    : col.equals("Marca") || col.equals("Modelo") || type == Catalogo.TIPO
                        ? 80
                        : 100;
        v[i] = Rules.required(text, col, max);
      }
    }
    if (type != Catalogo.EQUIPO && !Set.of("ACTIVO", "INACTIVO").contains(v[v.length - 1]))
      throw new BusinessException("Estado inválido.");
    return db.transaction(
        c -> {
          authorize(c, type != Catalogo.EQUIPO);
          if (type == Catalogo.EQUIPO) {
            if (!db.activeCatalog(c, Catalogo.TIPO, (int) v[2])
                || !db.activeCatalog(c, Catalogo.LABORATORIO, (int) v[6]))
              throw new BusinessException("Seleccione laboratorio y tipo activos.");
            if (id != null) {
              Equipo current = db.equipment(c, id, true);
              if (current == null) throw new BusinessException("Equipo inexistente.");
              if (current.estado() == EstadoEquipo.PRESTADO
                  || current.estado() == EstadoEquipo.MANTENIMIENTO)
                throw new BusinessException(
                    "No edite los datos de un equipo prestado o en mantenimiento.");
            }
          }
          int saved = db.saveCatalog(c, type, id, v);
          db.audit(
              c,
              session.usuarioId(),
              id == null ? "REGISTRO_CREADO" : "REGISTRO_ACTUALIZADO",
              type.tabla,
              saved,
              "Cambio de catálogo");
          return saved;
        });
  }

  public Equipo identify(String code) {
    String value = Rules.required(code, "Código", 30);
    return db.transaction(
        c -> {
          authorize(c, false);
          Equipo e = db.equipment(c, value);
          Rules.available(e);
          return e;
        });
  }

  public int loan(String who, List<Integer> ids, LocalDateTime end, String notes) {
    LocalDateTime start = LocalDateTime.now(java.time.ZoneId.of("America/Guatemala"));
    String person = Rules.required(who, "Solicitante", 150);
    Rules.loan(ids, start, end);
    String note = notes == null ? "" : notes;
    if (note.length() > 1000) throw new BusinessException("Observaciones: máximo 1000 caracteres.");
    return db.transaction(
        c -> {
          authorize(c, false);
          for (int id : ids.stream().sorted().toList()) Rules.available(db.equipment(c, id, true));
          int loan = db.createLoan(c, person, start, end, session.usuarioId(), note);
          for (int id : ids) {
            db.loanItem(c, loan, id);
            db.equipmentState(c, id, EstadoEquipo.PRESTADO);
          }
          db.audit(c, session.usuarioId(), "PRESTAMO_CREADO", "Prestamo", loan, "Equipos: " + ids);
          return loan;
        });
  }

  public void returnEquipment(int loanId, List<Integer> details, String condition, String notes) {
    if (details == null || details.isEmpty() || new HashSet<>(details).size() != details.size())
      throw new BusinessException("Seleccione detalles pendientes sin repetir.");
    EstadoEquipo next = Rules.returnState(condition);
    if (notes != null && notes.length() > 1000)
      throw new BusinessException("Observaciones: máximo 1000 caracteres.");
    db.transaction(
        c -> {
          authorize(c, false);
          Prestamo loan = db.loanLocked(c, loanId);
          for (int id : details.stream().sorted().toList()) {
            DetallePrestamo d = db.itemLocked(c, id);
            Rules.returnable(loan, d);
            Equipo e = db.equipment(c, d.equipoId(), true);
            if (e == null || e.estado() != EstadoEquipo.PRESTADO)
              throw new BusinessException(
                  "Estado inconsistente del equipo; no se modificó el préstamo.");
            db.returnItem(
                c,
                id,
                condition,
                notes,
                session.usuarioId(),
                LocalDateTime.now(java.time.ZoneId.of("America/Guatemala")));
            db.equipmentState(c, e.id(), next);
            db.audit(
                c, session.usuarioId(), "DEVOLUCION_REGISTRADA", "DetallePrestamo", id, condition);
          }
          db.closeLoanIfComplete(c, loanId);
          return null;
        });
  }

  public int startMaintenance(
      int equipment, String type, String description, String person, String notes) {
    String desc = Rules.required(description, "Descripción", 1000),
        responsible = Rules.required(person, "Responsable", 150);
    if (notes != null && notes.length() > 1000)
      throw new BusinessException("Observaciones demasiado largas.");
    return db.transaction(
        c -> {
          authorize(c, false);
          Equipo e = db.equipment(c, equipment, true);
          Rules.maintenance(e, type);
          int id =
              db.startMaintenance(
                  c, equipment, type, desc, responsible, notes, session.usuarioId());
          db.equipmentState(c, equipment, EstadoEquipo.MANTENIMIENTO);
          db.audit(
              c, session.usuarioId(), "EQUIPO_ENVIADO_MANTENIMIENTO", "Mantenimiento", id, type);
          return id;
        });
  }

  public void finishMaintenance(int id, String result) {
    EstadoEquipo next = Rules.maintenanceResult(result);
    db.transaction(
        c -> {
          authorize(c, false);
          Mantenimiento m = db.maintenanceLocked(c, id);
          if (m == null || !m.estado().equals("ABIERTO"))
            throw new BusinessException("El mantenimiento no está abierto.");
          Equipo e = db.equipment(c, m.equipoId(), true);
          if (e.estado() != EstadoEquipo.MANTENIMIENTO)
            throw new BusinessException("Estado de equipo inconsistente.");
          db.finishMaintenance(c, id, result);
          db.equipmentState(c, m.equipoId(), next);
          db.audit(c, session.usuarioId(), "MANTENIMIENTO_FINALIZADO", "Mantenimiento", id, result);
          return null;
        });
  }

  public Tabla details(int id) {
    return db.transaction(
        c -> {
          authorize(c, false);
          return db.details(c, id);
        });
  }

  public Tabla maintenance() {
    return db.transaction(
        c -> {
          authorize(c, false);
          return db.maintenance(c);
        });
  }

  public Tabla dashboard() {
    return db.transaction(
        c -> {
          authorize(c, false);
          return db.dashboard(c);
        });
  }

  public Tabla audit() {
    return db.transaction(
        c -> {
          authorize(c, true);
          return db.auditList(c);
        });
  }

  public Tabla loans(String state, LocalDate from, LocalDate to) {
    if (from == null || to == null || to.isBefore(from))
      throw new BusinessException("Rango de fechas inválido.");
    return db.transaction(
        c -> {
          authorize(c, false);
          return db.loans(c, state, from, to);
        });
  }

  public Tabla equipmentReport(String state) {
    return db.transaction(
        c -> {
          authorize(c, false);
          return db.equipmentReport(c, state);
        });
  }

  public Tabla history(int id, boolean maintenance) {
    return db.transaction(
        c -> {
          authorize(c, false);
          return maintenance ? db.maintenanceHistory(c, id) : db.equipmentHistory(c, id);
        });
  }
}
