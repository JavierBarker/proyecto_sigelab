package proyecto_sigelab.controller;

import proyecto_sigelab.model.*;
import proyecto_sigelab.repository.SqlRepository;
import proyecto_sigelab.service.*;
import proyecto_sigelab.view.*;

public final class LoginController {
  private final LoginView view;
  private final AuthService auth;
  private final SqlRepository db;

  public LoginController(SqlRepository db, AuthService auth) {
    this.db = db;
    this.auth = auth;
    view = new LoginView();
    view.login.addActionListener(
        e -> {
          String u = view.username.getText(), p = new String(view.password.getPassword());
          Tasks.run(
              view,
              () -> auth.login(u, p),
              s -> {
                view.password.setText("");
                view.dispose();
                new MainController(db, auth, s);
              });
        });
    view.enroll.addActionListener(
        e -> {
          String u = view.username.getText(), p = new String(view.password.getPassword());
          Tasks.run(
              view,
              () -> {
                auth.initialEnrollment(u, p);
                return true;
              },
              ok -> Forms.info(view, "Rostro registrado. Ahora pulse Ingresar."));
        });
    view.bootstrap.addActionListener(
        e -> {
          Forms f =
              new Forms()
                  .text("Nombre", "")
                  .text("Apellido", "")
                  .text("Usuario", "")
                  .text("Correo", "")
                  .password("Contraseña");
          if (f.show(view, "Administrador inicial (solo si no existen usuarios)"))
            Tasks.run(
                view,
                () ->
                    auth.saveUser(
                        null,
                        null,
                        f.value("Nombre"),
                        f.value("Apellido"),
                        f.value("Usuario"),
                        f.value("Contraseña"),
                        f.value("Correo"),
                        "ADMIN",
                        "ACTIVO"),
                id -> {
                  view.username.setText(f.value("Usuario"));
                  Forms.info(
                      view,
                      "Administrador creado. Escriba su contraseña y registre su rostro antes de"
                          + " ingresar.");
                });
        });
    view.setVisible(true);
  }
}
