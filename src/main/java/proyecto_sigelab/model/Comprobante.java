package proyecto_sigelab.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Datos de un comprobante de movimiento de equipo (préstamo, devolución, daño o mantenimiento).
 *
 * @param titulo título que encabeza el documento, por ejemplo "Comprobante de préstamo"
 * @param folio identificador del comprobante, por ejemplo "PRE-000012"
 * @param fecha fecha y hora del movimiento (hora de Guatemala)
 * @param equipo filas "etiqueta → valor" con los datos del equipo
 * @param detalle filas "etiqueta → valor" con el motivo y los datos del movimiento
 * @param responsable usuario de SIGELAB que registró el movimiento
 */
public record Comprobante(
    String titulo,
    String folio,
    LocalDateTime fecha,
    List<Map.Entry<String, String>> equipo,
    List<Map.Entry<String, String>> detalle,
    String responsable) {}
