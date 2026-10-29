package proyecto_sigelab.controller;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import javax.swing.*;
import proyecto_sigelab.integration.QrService;
import proyecto_sigelab.model.*;
import proyecto_sigelab.repository.SqlRepository;
import proyecto_sigelab.service.*;
import proyecto_sigelab.view.*;

public final class MainController {
  private final MainView view;
  private final InventoryService service;
  private final AuthService auth;
  private final Session session;
  private final QrService qr = new QrService();
  private final java.util.List<Runnable> refreshers = new ArrayList<>();

  public MainController(SqlRepository db, AuthService auth, Session session) {
    this.auth = auth;
    this.session = session;
    service = new InventoryService(db, session);
    view = new MainView(session);
    panel("Resumen", service::dashboard);
    if (session.rol().equals("ADMIN")) {
      users();
      catalog(Catalogo.LABORATORIO, "Laboratorios");
      catalog(Catalogo.TIPO, "Tipos de equipo");
    }
    catalog(Catalogo.EQUIPO, "Equipos");
    loans();
    maintenance();
    reports();
    if (session.rol().equals("ADMIN")) panel("Auditoría", service::audit);
    JMenuBar bar = new JMenuBar();
    JMenu account = new JMenu("Sesión");
    JMenuItem logout = new JMenuItem("Cerrar sesión");
    logout.addActionListener(
        e -> {
          view.dispose();
          new LoginController(db, auth);
        });
    account.add(logout);
    bar.add(account);
    view.setJMenuBar(bar);
    view.setVisible(true);
    // Cada pestaña se actualiza por demanda para evitar varias operaciones superpuestas.
    view.tabs.addChangeListener(
        e -> {
          int index = view.tabs.getSelectedIndex();
          if (index >= 0 && index < refreshers.size()) refreshers.get(index).run();
        });
    refreshers.get(0).run();
  }

  private DataPanel panel(String title, Supplier<Tabla> load) {
    DataPanel p = view.tab(title);
    Runnable refresh = () -> Tasks.run(view, load::get, p::display);
    p.button("Actualizar", refresh);
    refreshers.add(refresh);
    return p;
  }

  private void reload(DataPanel p, Supplier<Tabla> loader) {
    Tasks.run(view, loader::get, p::display);
  }

  private void catalog(Catalogo type, String title) {
    DataPanel p = panel(title, () -> service.catalog(type));
    p.button("Nuevo", () -> editCatalog(type, p, null));
    p.button("Editar", () -> Tasks.guarded(view, () -> editCatalog(type, p, p.selected())));
    if (type == Catalogo.EQUIPO)
      p.button(
          "Guardar QR",
          () ->
              Tasks.guarded(
                  view,
                  () -> {
                    var row = p.selected();
                    JFileChooser chooser = new JFileChooser();
                    chooser.setSelectedFile(new java.io.File(row.get(1) + ".png"));
                    if (chooser.showSaveDialog(view) == JFileChooser.APPROVE_OPTION) {
                      Path file = chooser.getSelectedFile().toPath();
                      if (Files.exists(file)
                          && JOptionPane.showConfirmDialog(
                                  view, "¿Reemplazar el archivo?", "QR", JOptionPane.YES_NO_OPTION)
                              != JOptionPane.YES_OPTION) return;
                      qr.generate(row.get(1).toString(), file);
                      Forms.info(view, "QR guardado. Contiene únicamente el código de inventario.");
                    }
                  }));
  }

  private void editCatalog(Catalogo type, DataPanel p, java.util.List<Object> row) {
    Tasks.run(
        view,
        () ->
            type == Catalogo.EQUIPO
                ? java.util.List.of(
                    service.catalog(Catalogo.TIPO), service.catalog(Catalogo.LABORATORIO))
                : java.util.List.<Tabla>of(),
        choices -> {
          Forms f = new Forms();
          for (int i = 0; i < type.columnas.length; i++) {
            String col = type.columnas[i];
            String value = row == null ? "" : Objects.toString(row.get(i + 1), "");
            if (col.equals("Estado")) {
              f.choices(col, "ACTIVO", "INACTIVO");
              f.select(col, value);
            } else if (col.equals("TipoEquipoId") || col.equals("LaboratorioId")) {
              Tabla data = choices.get(col.equals("TipoEquipoId") ? 0 : 1);
              java.util.List<Forms.Option> options = new ArrayList<>();
              for (var a : data.filas())
                if (a.get(a.size() - 1).equals("ACTIVO"))
                  options.add(
                      new Forms.Option(
                          ((Number) a.get(0)).intValue(),
                          a.get(col.equals("TipoEquipoId") ? 1 : 2).toString()));
              f.options(col, options);
              f.select(col, value);
            } else f.text(col, value);
          }
          if (f.show(view, (row == null ? "Crear " : "Editar ") + type.tabla)) {
            Object[] data = Arrays.stream(type.columnas).map(f::value).toArray();
            Tasks.run(
                view,
                () ->
                    service.save(type, row == null ? null : ((Number) row.get(0)).intValue(), data),
                id -> reload(p, () -> service.catalog(type)));
          }
        });
  }

  private void users() {
    DataPanel p = panel("Usuarios", service::users);
    p.button("Nuevo", () -> editUser(p, null));
    p.button("Editar / desactivar", () -> Tasks.guarded(view, () -> editUser(p, p.selected())));
    p.button(
        "Registrar / reemplazar rostro",
        () ->
            Tasks.guarded(
                view,
                () -> {
                  int id = p.selectedId();
                  if (JOptionPane.showConfirmDialog(
                          view,
                          "La persona seleccionada debe estar frente a la cámara. ¿Registrar o"
                              + " reemplazar su rostro?",
                          "Registro facial",
                          JOptionPane.YES_NO_OPTION)
                      == JOptionPane.YES_OPTION)
                    Tasks.run(
                        view,
                        () -> {
                          auth.enrollAsAdmin(session, id, true);
                          return true;
                        },
                        ok -> Forms.info(view, "Perfil facial registrado."));
                }));
  }

  private void editUser(DataPanel p, java.util.List<Object> row) {
    Forms f =
        new Forms()
            .text("Nombre", value(row, 1))
            .text("Apellido", value(row, 2))
            .text("Usuario", value(row, 3))
            .text("Correo", value(row, 4))
            .password("Contraseña (vacía = conservar)")
            .choices("Rol", "ADMIN", "ENCARGADO")
            .choices("Estado", "ACTIVO", "INACTIVO");
    if (row != null) {
      f.select("Rol", row.get(5));
      f.select("Estado", row.get(6));
    }
    if (f.show(view, row == null ? "Crear usuario" : "Editar usuario"))
      Tasks.run(
          view,
          () ->
              auth.saveUser(
                  session,
                  row == null ? null : ((Number) row.get(0)).intValue(),
                  f.value("Nombre"),
                  f.value("Apellido"),
                  f.value("Usuario"),
                  f.value("Contraseña (vacía = conservar)"),
                  f.value("Correo"),
                  f.value("Rol"),
                  f.value("Estado")),
          id -> reload(p, service::users));
  }

  private String value(java.util.List<Object> row, int index) {
    return row == null ? "" : Objects.toString(row.get(index), "");
  }

  private Tabla allLoans() {
    return service.loans("TODOS", LocalDate.of(1900, 1, 1), LocalDate.of(9999, 12, 31));
  }

  private void loans() {
    DataPanel p = panel("Préstamos y devoluciones", this::allLoans);
    p.button("Nuevo préstamo", () -> newLoan(p));
    p.button(
        "Ver detalle / devolver", () -> Tasks.guarded(view, () -> showDetails(p.selectedId())));
  }

  private void newLoan(DataPanel panel) {
    LoanDialog d = new LoanDialog(view);
    Runnable add =
        () -> {
          String code = d.code.getText();
          d.busy(true);
          new SwingWorker<Equipo, Void>() {
            protected Equipo doInBackground() {
              return service.identify(code);
            }

            protected void done() {
              d.busy(false);
              try {
                d.append(get());
              } catch (Exception ex) {
                Forms.error(
                    d, ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
              }
            }
          }.execute();
        };
    d.add.addActionListener(e -> add.run());
    d.qr.addActionListener(
        e -> {
          try {
            JFileChooser f = new JFileChooser();
            if (f.showOpenDialog(d) == JFileChooser.APPROVE_OPTION) {
              d.code.setText(qr.read(f.getSelectedFile().toPath()));
              add.run();
            }
          } catch (Exception ex) {
            Forms.error(d, ex.getMessage());
          }
        });
    d.confirm.addActionListener(
        e -> {
          try {
            String person = d.person.getText(), notes = d.notes.getText();
            LocalDateTime due = LocalDateTime.parse(d.due.getText().trim());
            var ids = d.ids();
            d.busy(true);
            new SwingWorker<Integer, Void>() {
              protected Integer doInBackground() {
                return service.loan(person, ids, due, notes);
              }

              protected void done() {
                d.busy(false);
                try {
                  int id = get();
                  d.dispose();
                  Forms.info(view, "Préstamo #" + id + " creado.");
                  reload(panel, MainController.this::allLoans);
                } catch (Exception ex) {
                  Forms.error(
                      d, ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
                }
              }
            }.execute();
          } catch (Exception ex) {
            Forms.error(d, "Revise la fecha. Formato: AAAA-MM-DDTHH:mm");
          }
        });
    d.setVisible(true);
  }

  private void showDetails(int id) {
    Tasks.run(
        view,
        () -> service.details(id),
        data -> {
          JDialog d =
              new JDialog(view, "Préstamo #" + id + " · seleccione equipos pendientes", false);
          DataPanel p = new DataPanel();
          p.display(data);
          p.button(
              "Devolver seleccionados",
              () -> {
                var ids = p.selectedIds();
                if (ids.isEmpty()) {
                  Forms.error(d, "Seleccione uno o varios equipos pendientes.");
                  return;
                }
                Forms f =
                    new Forms().choices("Condición", "BUENO", "DANADO").text("Observaciones", "");
                if (f.show(d, "Registrar devolución")) {
                  d.dispose();
                  Tasks.run(
                      view,
                      () -> {
                        service.returnEquipment(
                            id, ids, f.value("Condición"), f.value("Observaciones"));
                        return true;
                      },
                      ok -> {
                        Forms.info(view, "Devolución registrada.");
                        showDetails(id);
                      });
                }
              });
          d.setContentPane(p);
          d.setSize(850, 420);
          d.setLocationRelativeTo(view);
          d.setVisible(true);
        });
  }

  private void maintenance() {
    DataPanel p = panel("Mantenimiento", service::maintenance);
    p.button(
        "Enviar equipo",
        () ->
            Tasks.run(
                view,
                () -> service.equipmentReport("TODOS"),
                data -> {
                  java.util.List<Forms.Option> options = new ArrayList<>();
                  for (var a : data.filas())
                    if (Set.of("DISPONIBLE", "DANADO").contains(a.get(5)))
                      options.add(
                          new Forms.Option(
                              ((Number) a.get(0)).intValue(), a.get(1) + " · " + a.get(2)));
                  Forms f =
                      new Forms()
                          .options("Equipo", options)
                          .choices("Tipo", "PREVENTIVO", "CORRECTIVO")
                          .text("Descripción", "")
                          .text("Responsable", session.nombre())
                          .text("Observaciones", "");
                  if (f.show(view, "Enviar a mantenimiento"))
                    Tasks.run(
                        view,
                        () ->
                            service.startMaintenance(
                                Integer.parseInt(f.value("Equipo")),
                                f.value("Tipo"),
                                f.value("Descripción"),
                                f.value("Responsable"),
                                f.value("Observaciones")),
                        id -> reload(p, service::maintenance));
                }));
    p.button(
        "Finalizar",
        () ->
            Tasks.guarded(
                view,
                () -> {
                  int id = p.selectedId();
                  Forms f = new Forms().choices("Resultado", "REPARADO", "NO_REPARABLE");
                  if (f.show(view, "Cerrar mantenimiento #" + id))
                    Tasks.run(
                        view,
                        () -> {
                          service.finishMaintenance(id, f.value("Resultado"));
                          return true;
                        },
                        ok -> reload(p, service::maintenance));
                }));
  }

  private void reports() {
    DataPanel p = panel("Reportes", () -> service.equipmentReport("TODOS"));
    p.button(
        "Equipos por estado",
        () -> {
          Forms f =
              new Forms()
                  .choices(
                      "Estado",
                      "TODOS",
                      "DISPONIBLE",
                      "PRESTADO",
                      "DANADO",
                      "MANTENIMIENTO",
                      "BAJA");
          if (f.show(view, "Filtrar equipos"))
            reload(p, () -> service.equipmentReport(f.value("Estado")));
        });
    p.button(
        "Préstamos por fecha / estado",
        () -> {
          Forms f =
              new Forms()
                  .choices("Estado", "TODOS", "ACTIVO", "FINALIZADO", "VENCIDO")
                  .text(
                      "Desde (AAAA-MM-DD)",
                      LocalDate.now(java.time.ZoneId.of("America/Guatemala"))
                          .minusMonths(1)
                          .toString())
                  .text(
                      "Hasta (AAAA-MM-DD)",
                      LocalDate.now(java.time.ZoneId.of("America/Guatemala")).toString());
          if (f.show(view, "Consultar préstamos"))
            Tasks.run(
                view,
                () ->
                    service.loans(
                        f.value("Estado"),
                        LocalDate.parse(f.value("Desde (AAAA-MM-DD)")),
                        LocalDate.parse(f.value("Hasta (AAAA-MM-DD)"))),
                p::display);
        });
    p.button(
        "Historial de equipo",
        () ->
            Tasks.run(
                view,
                () -> service.equipmentReport("TODOS"),
                data -> {
                  var options =
                      data.filas().stream()
                          .map(
                              a ->
                                  new Forms.Option(
                                      ((Number) a.get(0)).intValue(), a.get(1) + " · " + a.get(2)))
                          .toList();
                  Forms f =
                      new Forms()
                          .options("Equipo", options)
                          .choices("Historial", "PRESTAMOS", "MANTENIMIENTOS");
                  if (f.show(view, "Historial"))
                    reload(
                        p,
                        () ->
                            service.history(
                                Integer.parseInt(f.value("Equipo")),
                                f.value("Historial").equals("MANTENIMIENTOS")));
                }));
  }
}
