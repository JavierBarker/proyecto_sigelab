package proyecto_sigelab.controller;

import java.awt.Desktop;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.*;
import proyecto_sigelab.integration.ComprobantePdf;
import proyecto_sigelab.integration.QrService;
import proyecto_sigelab.model.*;
import proyecto_sigelab.service.InventoryService;
import proyecto_sigelab.view.EquipoQrDialog;
import proyecto_sigelab.view.Forms;

/**
 * Flujo del QR: se escanea el código de un equipo, se muestra su historial y se registra una
 * acción (préstamo, devolución, daño o mantenimiento) que genera un comprobante en PDF.
 */
public final class EquipoQrController {
  private static final ZoneId GT = ZoneId.of("America/Guatemala");
  private static final Path CARPETA = Path.of("comprobantes");

  private final JFrame view;
  private final InventoryService service;
  private final Session session;
  private final QrService qr;
  private final Runnable alTerminar;

  public EquipoQrController(
      JFrame view, InventoryService service, Session session, QrService qr, Runnable alTerminar) {
    this.view = view;
    this.service = service;
    this.session = session;
    this.qr = qr;
    this.alTerminar = alTerminar;
  }

  /** Pide el código: desde una imagen QR o escrito / leído con un lector USB. */
  public void scan() {
    Object[] opciones = {"Leer imagen QR", "Escribir o usar lector", "Cancelar"};
    int opcion =
        JOptionPane.showOptionDialog(
            view,
            "¿Cómo desea identificar el equipo?",
            "Escanear QR",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            opciones,
            opciones[0]);
    String code = null;
    if (opcion == 0) {
      JFileChooser chooser = new JFileChooser();
      if (chooser.showOpenDialog(view) != JFileChooser.APPROVE_OPTION) return;
      try {
        code = qr.read(chooser.getSelectedFile().toPath());
      } catch (RuntimeException e) {
        Forms.error(view, e.getMessage());
        return;
      }
    } else if (opcion == 1) {
      code = JOptionPane.showInputDialog(view, "Código de inventario (ej. CAM-001):", "Escanear QR", JOptionPane.PLAIN_MESSAGE);
    }
    if (code != null && !code.isBlank()) open(code.trim());
  }

  /** Busca el equipo y abre su ventana con historial y acciones. */
  public void open(String code) {
    Tasks.run(
        view,
        () -> {
          Equipo e = service.lookup(code);
          return new Object[] {e, service.equipmentInfo(e.id()), service.timeline(e.id())};
        },
        data -> {
          Equipo e = (Equipo) data[0];
          @SuppressWarnings("unchecked")
          List<Object> info = (List<Object>) data[1];
          Tabla historial = (Tabla) data[2];
          show(e, info, historial);
        });
  }

  private void show(Equipo e, List<Object> info, Tabla historial) {
    EquipoQrDialog d = new EquipoQrDialog(view, info, historial);
    switch (e.estado()) {
      case DISPONIBLE -> {
        d.action("Prestar", true, () -> prestar(d, e, info));
        d.action("Reportar daño", false, () -> reportarDano(d, e, info));
        d.action("Enviar a mantenimiento", false, () -> mantenimiento(d, e, info));
      }
      case PRESTADO -> d.action("Registrar devolución", true, () -> devolver(d, e, info));
      case DANADO -> d.action("Enviar a mantenimiento", true, () -> mantenimiento(d, e, info));
      case MANTENIMIENTO -> d.action("Finalizar mantenimiento", true, () -> finalizar(d, e, info));
      case BAJA -> d.note("Equipo dado de baja: no admite movimientos.");
    }
    JButton ultimo = d.action("Comprobante del último movimiento", false, () -> ultimo(e, info, historial));
    ultimo.setEnabled(!historial.filas().isEmpty());
    d.setVisible(true);
  }

  // ---------- Acciones ----------

  private void prestar(EquipoQrDialog d, Equipo e, List<Object> info) {
    Forms f =
        new Forms()
            .text("Solicitante", "")
            .text(
                "Devolución (AAAA-MM-DDTHH:mm)",
                LocalDateTime.now(GT).plusDays(1).withSecond(0).withNano(0).toString())
            .text("Observaciones", "");
    if (!f.show(d, "Prestar " + e.codigo())) return;
    LocalDateTime due;
    try {
      due = LocalDateTime.parse(f.value("Devolución (AAAA-MM-DDTHH:mm)").trim());
    } catch (RuntimeException ex) {
      Forms.error(d, "Revise la fecha. Formato: AAAA-MM-DDTHH:mm");
      return;
    }
    String persona = f.value("Solicitante"), notas = f.value("Observaciones");
    d.dispose();
    Tasks.run(
        view,
        () -> service.loan(persona, List.of(e.id()), due, notas),
        id ->
            emitir(
                "Comprobante de préstamo",
                folio("PRE", id),
                info,
                "PRESTADO",
                List.of(
                    Map.entry("Motivo", "Préstamo de equipo"),
                    Map.entry("Préstamo", "#" + id),
                    Map.entry("Solicitante", persona),
                    Map.entry("Devolución esperada", due.toString().replace('T', ' ')),
                    Map.entry("Observaciones", vacio(notas))),
                e.codigo()));
  }

  private void devolver(EquipoQrDialog d, Equipo e, List<Object> info) {
    Forms f = new Forms().choices("Condición", "BUENO", "DANADO").text("Observaciones", "");
    if (!f.show(d, "Devolución de " + e.codigo())) return;
    String condicion = f.value("Condición"), notas = f.value("Observaciones");
    d.dispose();
    Tasks.run(
        view,
        () -> {
          List<Object> pendiente = service.pendingLoan(e.id());
          int detalle = ((Number) pendiente.get(0)).intValue();
          int prestamo = ((Number) pendiente.get(1)).intValue();
          service.returnEquipment(prestamo, List.of(detalle), condicion, notas);
          return pendiente;
        },
        p -> {
          boolean danado = condicion.equals("DANADO");
          int prestamo = ((Number) p.get(1)).intValue();
          emitir(
              danado ? "Reporte de daño en devolución" : "Comprobante de devolución",
              folio("DEV", ((Number) p.get(0)).intValue()),
              info,
              danado ? "DANADO" : "DISPONIBLE",
              List.of(
                  Map.entry("Motivo", danado ? "Devolución con daño" : "Devolución en buen estado"),
                  Map.entry("Préstamo", "#" + prestamo),
                  Map.entry("Solicitante", Objects.toString(p.get(2), "")),
                  Map.entry("Fecha de préstamo", fecha(p.get(3))),
                  Map.entry("Devolución esperada", fecha(p.get(4))),
                  Map.entry("Condición", condicion),
                  Map.entry("Observaciones", vacio(notas))),
              e.codigo());
        });
  }

  private void reportarDano(EquipoQrDialog d, Equipo e, List<Object> info) {
    Forms f = new Forms().text("Descripción del daño", "").text("Reportado por", "");
    if (!f.show(d, "Reportar daño de " + e.codigo())) return;
    String descripcion = f.value("Descripción del daño"), quien = f.value("Reportado por");
    String detalle = quien.isBlank() ? descripcion : descripcion + " (reportado por " + quien.trim() + ")";
    d.dispose();
    Tasks.run(
        view,
        () -> {
          service.reportDamage(e.id(), detalle);
          return LocalDateTime.now(GT);
        },
        cuando ->
            emitir(
                "Reporte de daño",
                "DAN-" + e.codigo() + "-" + cuando.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm")),
                info,
                "DANADO",
                List.of(
                    Map.entry("Motivo", "Daño reportado en laboratorio"),
                    Map.entry("Descripción", descripcion),
                    Map.entry("Reportado por", vacio(quien))),
                e.codigo()));
  }

  private void mantenimiento(EquipoQrDialog d, Equipo e, List<Object> info) {
    Forms f =
        new Forms()
            .choices("Tipo", e.estado() == EstadoEquipo.DANADO ? "CORRECTIVO" : "PREVENTIVO",
                e.estado() == EstadoEquipo.DANADO ? "PREVENTIVO" : "CORRECTIVO")
            .text("Descripción", "")
            .text("Responsable", session.nombre())
            .text("Observaciones", "");
    if (!f.show(d, "Enviar " + e.codigo() + " a mantenimiento")) return;
    String tipo = f.value("Tipo"), desc = f.value("Descripción"), resp = f.value("Responsable"),
        notas = f.value("Observaciones");
    d.dispose();
    Tasks.run(
        view,
        () -> service.startMaintenance(e.id(), tipo, desc, resp, notas),
        id ->
            emitir(
                "Orden de mantenimiento",
                folio("MAN", id),
                info,
                "MANTENIMIENTO",
                List.of(
                    Map.entry("Motivo", "Ingreso a mantenimiento " + tipo.toLowerCase()),
                    Map.entry("Mantenimiento", "#" + id),
                    Map.entry("Descripción", desc),
                    Map.entry("Responsable técnico", resp),
                    Map.entry("Observaciones", vacio(notas))),
                e.codigo()));
  }

  private void finalizar(EquipoQrDialog d, Equipo e, List<Object> info) {
    Forms f = new Forms().choices("Resultado", "REPARADO", "NO_REPARABLE");
    if (!f.show(d, "Finalizar mantenimiento de " + e.codigo())) return;
    String resultado = f.value("Resultado");
    d.dispose();
    Tasks.run(
        view,
        () -> {
          int id = service.openMaintenance(e.id());
          service.finishMaintenance(id, resultado);
          return id;
        },
        id ->
            emitir(
                "Cierre de mantenimiento",
                folio("MAN", id) + "-C",
                info,
                resultado.equals("REPARADO") ? "DISPONIBLE" : "BAJA",
                List.of(
                    Map.entry("Motivo", "Finalización de mantenimiento"),
                    Map.entry("Mantenimiento", "#" + id),
                    Map.entry(
                        "Resultado",
                        resultado.equals("REPARADO")
                            ? "REPARADO · el equipo vuelve a estar disponible"
                            : "NO REPARABLE · el equipo se da de baja")),
                e.codigo()));
  }

  /** Comprobante del movimiento más reciente del historial (no registra nada nuevo). */
  private void ultimo(Equipo e, List<Object> info, Tabla historial) {
    List<Object> mov = historial.filas().get(0);
    List<Map.Entry<String, String>> detalle = new ArrayList<>();
    detalle.add(Map.entry("Movimiento", Objects.toString(mov.get(1), "")));
    detalle.add(Map.entry("Referencia", Objects.toString(mov.get(2), "")));
    detalle.add(Map.entry("Fecha del movimiento", fecha(mov.get(0))));
    detalle.add(Map.entry("Detalle", vacio(Objects.toString(mov.get(3), ""))));
    Comprobante c =
        new Comprobante(
            "Constancia de movimiento",
            "MOV-" + e.codigo(),
            LocalDateTime.now(GT),
            datosEquipo(info, Objects.toString(info.get(4), "")),
            detalle,
            session.nombre() + " · " + session.rol());
    guardarYAbrir(c);
  }

  // ---------- Comprobante ----------

  private void emitir(
      String titulo,
      String folio,
      List<Object> info,
      String estadoFinal,
      List<Map.Entry<String, String>> detalle,
      String codigo) {
    Comprobante c =
        new Comprobante(
            titulo,
            folio,
            LocalDateTime.now(GT),
            datosEquipo(info, estadoFinal),
            detalle,
            session.nombre() + " · " + session.rol());
    guardarYAbrir(c);
    if (alTerminar != null) alTerminar.run();
    open(codigo);
  }

  private void guardarYAbrir(Comprobante c) {
    try {
      Path archivo = ComprobantePdf.generate(c, CARPETA);
      Path absoluto = archivo.toAbsolutePath();
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        Desktop.getDesktop().open(absoluto.toFile());
        Forms.info(view, "Movimiento registrado. Comprobante guardado en:\n" + absoluto);
      } else Forms.info(view, "Comprobante guardado en:\n" + absoluto);
    } catch (Exception ex) {
      Forms.error(view, "El movimiento se registró, pero no se pudo abrir el comprobante: " + ex.getMessage());
    }
  }

  private static List<Map.Entry<String, String>> datosEquipo(List<Object> info, String estado) {
    return List.of(
        Map.entry("Código", Objects.toString(info.get(0), "")),
        Map.entry("Equipo", Objects.toString(info.get(1), "")),
        Map.entry("Tipo", Objects.toString(info.get(2), "")),
        Map.entry("Laboratorio", Objects.toString(info.get(3), "")),
        Map.entry("Estado actual", estado));
  }

  private static String folio(String prefijo, int numero) {
    return String.format("%s-%06d", prefijo, numero);
  }

  private static String vacio(String s) {
    return s == null || s.isBlank() ? "—" : s.trim();
  }

  private static String fecha(Object v) {
    if (v == null) return "—";
    String s = v.toString().replace('T', ' ');
    return s.length() > 16 ? s.substring(0, 16) : s;
  }
}
