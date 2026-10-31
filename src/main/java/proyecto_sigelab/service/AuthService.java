package proyecto_sigelab.service;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.mindrot.jbcrypt.BCrypt;
import proyecto_sigelab.exception.BusinessException;
import proyecto_sigelab.integration.FacialClient;
import proyecto_sigelab.model.*;
import proyecto_sigelab.repository.SqlRepository;

public final class AuthService {
  private final SqlRepository db;
  private final FacialClient facial;

  public AuthService(SqlRepository db, FacialClient facial) {
    this.db = db;
    this.facial = facial;
  }

  private Usuario credentials(String username, String password) {
    Usuario u = db.transaction(c -> db.user(c, username == null ? "" : username.trim()));
    boolean valid = false;
    if (u != null && u.activo() && password != null)
      try {
        valid = BCrypt.checkpw(password, u.hash());
      } catch (IllegalArgumentException ignored) {
      }
    if (!valid)
      throw new BusinessException("Usuario o contraseña incorrectos, o usuario inactivo.");
    return u;
  }

  public Session login(String username, String password) {
    Usuario u;
    try {
      u = credentials(username, password);
      Rules.operator(u);
    } catch (BusinessException ex) {
      db.transaction(
          c -> {
            db.audit(c, null, "LOGIN_FALLIDO", "Usuario", null, "Credenciales rechazadas");
            return null;
          });
      throw ex;
    }
    try {
      facial.verify(u.id());
    } catch (BusinessException e) {
      db.transaction(
          c -> {
            db.audit(c, u.id(), "LOGIN_FALLIDO", "Usuario", u.id(), "Segundo factor rechazado");
            return null;
          });
      throw e;
    }
    return db.transaction(
        c -> {
          Usuario current = db.user(c, u.id());
          Rules.operator(current);
          if (!current.hash().equals(u.hash()))
            throw new BusinessException("La cuenta cambió durante el login. Inicie de nuevo.");
          db.audit(
              c, u.id(), "LOGIN_EXITOSO", "Usuario", u.id(), "Contraseña y rostro verificados");
          return new Session(current.id(), current.nombreCompleto(), current.rol());
        });
  }

  public void initialEnrollment(String username, String password) {
    Usuario u = credentials(username, password);
    if (facial.enrolled(u.id()))
      throw new BusinessException(
          "El rostro ya está registrado. Solicite a un ADMIN su actualización.");
    facial.enroll(u.id(), false);
    db.transaction(
        c -> {
          db.audit(
              c,
              u.id(),
              "PERFIL_FACIAL_REGISTRADO",
              "Usuario",
              u.id(),
              "Alta inicial con contraseña validada");
          return null;
        });
  }

  public void enrollAsAdmin(Session session, int user, boolean replace) {
    db.transaction(
        c -> {
          Rules.admin(db.user(c, session.usuarioId()));
          return null;
        });
    facial.enroll(user, replace);
    db.transaction(
        c -> {
          db.audit(
              c,
              session.usuarioId(),
              "PERFIL_FACIAL_REGISTRADO",
              "Usuario",
              user,
              replace ? "Perfil reemplazado" : "Alta facial");
          return null;
        });
  }

  public static String hashPassword(String password) {
    if (password == null
        || password.length() < 8
        || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new BusinessException(
          "La contraseña debe tener al menos 8 caracteres y como máximo 72 bytes UTF-8.");
    return BCrypt.hashpw(password, BCrypt.gensalt(12));
  }

  public int saveUser(
      Session session,
      Integer id,
      String name,
      String last,
      String username,
      String password,
      String email,
      String role,
      String state) {
    String n = Rules.required(name, "Nombre", 80),
        l = Rules.required(last, "Apellido", 80),
        u = Rules.required(username, "Usuario", 50),
        mail = Rules.required(email, "Correo", 254);
    if (!mail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new BusinessException("Correo inválido.");
    if (!Set.of("ADMIN", "ENCARGADO").contains(role)
        || !Set.of("ACTIVO", "INACTIVO").contains(state))
      throw new BusinessException("Rol o estado inválido.");
    String hash = password == null || password.isEmpty() ? null : hashPassword(password);
    return db.transaction(
        c -> {
          if (session == null) {
            if (id != null
                || !db.emptyUsersLocked(c)
                || !role.equals("ADMIN")
                || !state.equals("ACTIVO"))
              throw new BusinessException("El administrador inicial ya existe. Inicie sesión.");
          } else {
            Rules.admin(db.user(c, session.usuarioId()));
            if (id != null
                && id == session.usuarioId()
                && (!state.equals("ACTIVO") || !role.equals("ADMIN")))
              throw new BusinessException("No puede desactivar ni quitar su propio rol ADMIN.");
          }
          Usuario previous = id == null ? null : db.user(c, id);
          if (id != null && previous == null) throw new BusinessException("Usuario inexistente.");
          if (hash == null && previous == null)
            throw new BusinessException("Defina una contraseña para el nuevo usuario.");
          int saved =
              db.saveUser(c, id, n, l, u, hash == null ? previous.hash() : hash, mail, role, state);
          db.audit(
              c,
              session == null ? saved : session.usuarioId(),
              state.equals("INACTIVO")
                  ? "USUARIO_DESACTIVADO"
                  : id == null ? "USUARIO_CREADO" : "USUARIO_ACTUALIZADO",
              "Usuario",
              saved,
              "Rol: " + role);
          return saved;
        });
  }
}
