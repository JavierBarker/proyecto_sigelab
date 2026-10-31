package proyecto_sigelab.service;

import java.time.*;
import java.util.*;
import proyecto_sigelab.exception.BusinessException;
import proyecto_sigelab.model.*;

public final class Rules {
  private Rules() {}

  public static String required(String s, String label, int max) {
    if (s == null || s.isBlank()) throw new BusinessException(label + " es obligatorio.");
    s = s.trim();
    if (s.length() > max) throw new BusinessException(label + ": máximo " + max + " caracteres.");
    return s;
  }

  public static void loan(List<Integer> ids, LocalDateTime start, LocalDateTime end) {
    if (ids == null || ids.isEmpty()) throw new BusinessException("Agregue al menos un equipo.");
    if (new HashSet<>(ids).size() != ids.size())
      throw new BusinessException("No repita un equipo en el préstamo.");
    if (ids.stream().anyMatch(i -> i == null || i <= 0))
      throw new BusinessException("Equipo inválido.");
    if (end == null || !end.isAfter(start))
      throw new BusinessException("La devolución esperada debe ser posterior al préstamo.");
  }

  public static void available(Equipo e) {
    if (e == null) throw new BusinessException("El equipo no existe.");
    if (e.estado() != EstadoEquipo.DISPONIBLE)
      throw new BusinessException(e.codigo() + " está " + e.estado() + " y no puede prestarse.");
  }

  public static void returnable(Prestamo p, DetallePrestamo d) {
    if (p == null || !"ACTIVO".equals(p.estado()))
      throw new BusinessException("El préstamo no está activo.");
    if (d == null || d.prestamoId() != p.id() || d.devuelto())
      throw new BusinessException("El detalle no pertenece al préstamo o ya fue devuelto.");
  }

  public static EstadoEquipo returnState(String condition) {
    return switch (condition) {
      case "BUENO" -> EstadoEquipo.DISPONIBLE;
      case "DANADO" -> EstadoEquipo.DANADO;
      default -> throw new BusinessException("Condición de devolución inválida.");
    };
  }

  public static void maintenance(Equipo e, String type) {
    if (e == null) throw new BusinessException("Equipo inexistente.");
    if (!Set.of("PREVENTIVO", "CORRECTIVO").contains(type))
      throw new BusinessException("Tipo de mantenimiento inválido.");
    if (e.estado() != EstadoEquipo.DISPONIBLE && e.estado() != EstadoEquipo.DANADO)
      throw new BusinessException(
          "Solo puede enviar equipos disponibles o dañados a mantenimiento.");
  }

  public static EstadoEquipo maintenanceResult(String result) {
    return switch (result) {
      case "REPARADO" -> EstadoEquipo.DISPONIBLE;
      case "NO_REPARABLE" -> EstadoEquipo.BAJA;
      default -> throw new BusinessException("Resultado inválido.");
    };
  }

  public static void operator(Usuario u) {
    if (u == null || !u.activo() || !Set.of("ADMIN", "ENCARGADO").contains(u.rol()))
      throw new BusinessException("Sesión inactiva o sin permisos para operar.");
  }

  public static void admin(Usuario u) {
    operator(u);
    if (!"ADMIN".equals(u.rol())) throw new BusinessException("Esta operación requiere ADMIN.");
  }
}
