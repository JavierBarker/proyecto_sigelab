import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Genera los diagramas de documentación de SIGELAB en docs/diagramas/*.png.
 * Sin dependencias externas: java -Djava.awt.headless=true scripts/GenerarDiagramas.java
 *
 * Diagramas: flujo de login, préstamo, devolución y mantenimiento;
 * máquina de estados del equipo; casos de uso; y modelo entidad-relación.
 */
public final class GenerarDiagramas {

  // ---- Paleta -------------------------------------------------------------
  static final Color INK = new Color(0x263238);
  static final Color ARROW = new Color(0x37474F);
  static final Color PROC_BG = new Color(0xE3F2FD), PROC_FG = new Color(0x0D47A1);
  static final Color DEC_BG = new Color(0xFFF8E1), DEC_FG = new Color(0x5D4037);
  static final Color OK_BG = new Color(0xE8F5E9), OK_FG = new Color(0x1B5E20);
  static final Color ERR_BG = new Color(0xFFEBEE), ERR_FG = new Color(0xB71C1C);
  static final Color DB_BG = new Color(0xF3E5F5), DB_FG = new Color(0x4A148C);
  static final Color NOTE_BG = new Color(0xFFFDE7), NOTE_FG = new Color(0x827717);
  static final Color ER_HEAD = new Color(0x1F3A5F);
  static final Color ER_ROW = new Color(0xE8EEF6);
  static final Color ER_BOR = new Color(0x9FB4CC);
  static final Color ER_FK = new Color(0x24557E);

  record Box(int x, int y, int w, int h) {
    int cx() { return x + w / 2; }
    int cy() { return y + h / 2; }
  }

  // ---- Utilidades de texto -------------------------------------------------
  static String[] wrap(String s, int maxChars) {
    List<String> out = new ArrayList<>();
    for (String para : s.split("\n")) {
      StringBuilder line = new StringBuilder();
      for (String word : para.split(" ")) {
        if (word.isEmpty()) continue;
        if (line.length() == 0) line.append(word);
        else if (line.length() + 1 + word.length() <= maxChars)
          line.append(' ').append(word);
        else {
          out.add(line.toString());
          line = new StringBuilder(word);
        }
      }
      out.add(line.toString());
    }
    return out.toArray(new String[0]);
  }

  static BufferedImage canvas(int w, int h) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, w, h);
    return img;
  }

  static void heading(Graphics2D g, int w, String title, String subtitle) {
    g.setFont(new Font("SansSerif", Font.BOLD, 26));
    g.setColor(new Color(0x1F3A5F));
    g.drawString(title, 40, 44);
    g.setFont(new Font("SansSerif", Font.PLAIN, 16));
    g.setColor(Color.GRAY);
    g.drawString(subtitle, 40, 70);
  }

  // ---- Nodos ---------------------------------------------------------------
  static Box process(Graphics2D g, int x, int y, int w, String text, Color bg, Color fg) {
    g.setFont(new Font("SansSerif", Font.PLAIN, 15));
    String[] lines = wrap(text, (w - 30) / 7);
    int lineH = 22, pad = 12;
    int h = pad * 2 + lines.length * lineH;
    g.setColor(bg);
    g.fillRoundRect(x, y, w, h, 14, 14);
    g.setColor(fg.darker().darker());
    g.setStroke(new BasicStroke(1.6f));
    g.drawRoundRect(x, y, w, h, 14, 14);
    g.setColor(fg);
    FontMetrics fm = g.getFontMetrics();
    for (int i = 0; i < lines.length; i++)
      g.drawString(lines[i], x + w / 2 - fm.stringWidth(lines[i]) / 2,
          y + pad + lineH * i + fm.getAscent() + 2);
    return new Box(x, y, w, h);
  }

  static Box terminal(Graphics2D g, int x, int y, int w, String text, boolean ok) {
    Color bg = ok ? OK_BG : ERR_BG;
    Color fg = ok ? OK_FG : ERR_FG;
    g.setFont(new Font("SansSerif", Font.BOLD, 15));
    String[] lines = wrap(text, (w - 40) / 8);
    int lineH = 22, pad = 12;
    int h = pad * 2 + lines.length * lineH;
    g.setColor(bg);
    g.fillRoundRect(x, y, w, h, h / 2, h / 2);
    g.setColor(fg);
    g.setStroke(new BasicStroke(1.8f));
    g.drawRoundRect(x, y, w, h, h / 2, h / 2);
    FontMetrics fm = g.getFontMetrics();
    for (int i = 0; i < lines.length; i++)
      g.drawString(lines[i], x + w / 2 - fm.stringWidth(lines[i]) / 2,
          y + pad + lineH * i + fm.getAscent() + 2);
    return new Box(x, y, w, h);
  }

  /** Terminal de tamaño fijo con texto grande (usado en la máquina de estados). */
  static Box terminal(Graphics2D g, int x, int y, int w, int h, String text, boolean ok) {
    Color bg = ok ? OK_BG : ERR_BG;
    Color fg = ok ? OK_FG : ERR_FG;
    g.setColor(bg);
    g.fillRoundRect(x, y, w, h, h / 2, h / 2);
    g.setColor(fg);
    g.setStroke(new BasicStroke(2f));
    g.drawRoundRect(x, y, w, h, h / 2, h / 2);
    g.setFont(new Font("SansSerif", Font.BOLD, 18));
    FontMetrics fm = g.getFontMetrics();
    g.drawString(text, x + w / 2 - fm.stringWidth(text) / 2, y + h / 2 + fm.getAscent() / 2 - 2);
    return new Box(x, y, w, h);
  }

  static Box decision(Graphics2D g, int cx, int top, int w, int h, String text) {
    g.setFont(new Font("SansSerif", Font.BOLD, 15));
    String[] lines = wrap(text, (int) (w * 0.62 / 7.6));
    int[] xs = {cx - w / 2, cx, cx + w / 2, cx};
    int[] ys = {top + h / 2, top, top + h / 2, top + h};
    g.setColor(DEC_BG);
    g.fillPolygon(xs, ys, 4);
    g.setColor(DEC_FG);
    g.setStroke(new BasicStroke(1.6f));
    g.drawPolygon(xs, ys, 4);
    FontMetrics fm = g.getFontMetrics();
    int lineH = 20;
    int y0 = top + h / 2 - (lines.length - 1) * lineH / 2;
    for (int i = 0; i < lines.length; i++)
      g.drawString(lines[i], cx - fm.stringWidth(lines[i]) / 2, y0 + lineH * i);
    return new Box(cx - w / 2, top, w, h);
  }

  static Box note(Graphics2D g, int x, int y, int w, String text) {
    return process(g, x, y, w, text, NOTE_BG, NOTE_FG);
  }

  // ---- Flechas -------------------------------------------------------------
  static int[] anchor(Box b, String side, double f) {
    return switch (side) {
      case "top" -> new int[]{b.x() + (int) (b.w() * f), b.y()};
      case "bottom" -> new int[]{b.x() + (int) (b.w() * f), b.y() + b.h()};
      case "left" -> new int[]{b.x(), b.y() + (int) (b.h() * f)};
      case "right" -> new int[]{b.x() + b.w(), b.y() + (int) (b.h() * f)};
      default -> new int[]{b.cx(), b.cy()};
    };
  }

  static void arrowhead(Graphics2D g, int x1, int y1, int x2, int y2, boolean hollow) {
    double ang = Math.atan2(y2 - y1, x2 - x1);
    int len = 12, wid = 8;
    int[] tx = {
      x2,
      x2 - (int) (len * Math.cos(ang) - wid * Math.sin(ang)),
      x2 - (int) (len * Math.cos(ang) + wid * Math.sin(ang))
    };
    int[] ty = {
      y2,
      y2 - (int) (len * Math.sin(ang) + wid * Math.cos(ang)),
      y2 - (int) (len * Math.sin(ang) - wid * Math.cos(ang))
    };
    if (hollow) {
      g.setColor(Color.WHITE);
      g.fillPolygon(tx, ty, 3);
      g.setColor(ARROW);
      g.drawPolygon(tx, ty, 3);
    } else {
      g.setColor(ARROW);
      g.fillPolygon(tx, ty, 3);
    }
  }

  /** style: 0 = sólida, 1 = punteada, 2 = punteada con triángulo hueco (herencia). */
  static void link(Graphics2D g, Box a, String sa, double fa, Box b, String sb, double fb,
      String label, int style) {
    int[] p = anchor(a, sa, fa), q = anchor(b, sb, fb);
    g.setColor(ARROW);
    g.setStroke(style == 0 ? new BasicStroke(1.8f)
        : new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
            new float[]{7, 5}, 0));
    g.drawLine(p[0], p[1], q[0], q[1]);
    arrowhead(g, p[0], p[1], q[0], q[1], style == 2);
    if (label != null) {
      double t = 0.28;
      int lx = (int) (p[0] + (q[0] - p[0]) * t), ly = (int) (p[1] + (q[1] - p[1]) * t) - 8;
      g.setFont(new Font("SansSerif", Font.BOLD, 13));
      FontMetrics fm = g.getFontMetrics();
      int tw = fm.stringWidth(label);
      g.setColor(Color.WHITE);
      g.fillRect(lx - tw / 2 - 3, ly - fm.getAscent(), tw + 6, fm.getHeight());
      g.setColor(ARROW);
      g.drawString(label, lx - tw / 2, ly);
    }
  }

  static void link(Graphics2D g, Box a, String sa, double fa, Box b, String sb, double fb) {
    link(g, a, sa, fa, b, sb, fb, null, 0);
  }

  // =========================================================================
  // 1. FLUJO DE LOGIN
  // =========================================================================
  static void flujoLogin() throws Exception {
    int W = 1180, H = 1700, MX = 110, MW = 440, SX = 700, SW = 410;
    BufferedImage img = canvas(W, H);
    Graphics2D g = img.createGraphics();
    heading(g, W, "SIGELAB · Flujo de inicio de sesión",
        "Con contraseña (BCrypt) y segundo factor: reconocimiento facial obligatorio");

    int y = 100;
    Box inicio = terminal(g, MX + 40, y, MW - 80, "El usuario abre SIGELAB", true);
    y = inicio.y() + inicio.h() + 30;
    Box ventana = process(g, MX, y, MW,
        "Ventana de inicio de sesión: escribe su usuario y contraseña.", PROC_BG, PROC_FG);
    y = ventana.y() + ventana.h() + 34;
    Box hayUsuarios = decision(g, MX + MW / 2, y, 320, 130, "¿Hay usuarios registrados en la base?");

    int sideY = hayUsuarios.y() - 10;
    Box alta = process(g, SX, sideY, SW,
        "Primer arranque: botón «Crear administrador inicial». Se registran nombre, usuario,"
            + " correo y contraseña (mínimo 8 caracteres). La contraseña se guarda solo como hash"
            + " BCrypt, nunca en texto plano.", DB_BG, DB_FG);
    Box altaRostro = process(g, SX, alta.y() + alta.h() + 24, SW,
        "Botón «Registrar mi rostro»: la cámara captura el vector facial y se guarda cifrado en"
            + " PerfilFacial (dato biométrico, nunca una foto).", DB_BG, DB_FG);

    y = hayUsuarios.y() + hayUsuarios.h() + 34;
    Box credenciales = decision(g, MX + MW / 2, y, 360, 150,
        "¿La contraseña coincide (BCrypt) y el usuario está ACTIVO?");

    int e1y = credenciales.y() - 20;
    Box err1 = process(g, SX, e1y, SW,
        "Mensaje: «Usuario o contraseña incorrectos, o usuario inactivo».", ERR_BG, ERR_FG);
    Box aud1 = process(g, SX, err1.y() + err1.h() + 22, SW,
        "Auditoría: LOGIN_FALLIDO con el motivo. No se revela cuál campo falló.", DB_BG, DB_FG);
    Box fin1 = terminal(g, SX + 20, aud1.y() + aud1.h() + 24, SW - 40, "Acceso denegado", false);

    y = credenciales.y() + credenciales.h() + 34;
    Box llama = process(g, MX, y, MW,
        "La aplicación llama al servicio facial local (Python, http://127.0.0.1:8765, con token"
            + " compartido) mientras muestra «Mire a la cámara…».", PROC_BG, PROC_FG);
    y = llama.y() + llama.h() + 24;
    Box cam = process(g, MX, y, MW,
        "La cámara captura el rostro. El servicio comprueba la prueba de vida (liveness) y compara"
            + " el vector contra el perfil registrado del usuario.", PROC_BG, PROC_FG);
    y = cam.y() + cam.h() + 30;
    Box coincide = decision(g, MX + MW / 2, y, 320, 140, "¿El rostro coincide con el usuario?");

    int e2y = coincide.y() - 10;
    Box err2 = process(g, SX, e2y, SW,
        "Mensaje claro según el caso: rostro no reconocido, no hay rostro registrado o servicio"
            + " no disponible. Regla de falla cerrada: si no se pudo verificar, NO se admite.",
        ERR_BG, ERR_FG);
    Box aud2 = process(g, SX, err2.y() + err2.h() + 22, SW,
        "Auditoría: LOGIN_FALLIDO (segundo factor rechazado).", DB_BG, DB_FG);
    Box fin2 = terminal(g, SX + 20, aud2.y() + aud2.h() + 24, SW - 40, "Acceso denegado", false);

    y = coincide.y() + coincide.h() + 30;
    Box audOk = process(g, MX, y, MW, "Auditoría: LOGIN_EXITOSO.", DB_BG, DB_FG);
    y = audOk.y() + audOk.h() + 26;
    Box finOk = terminal(g, MX + 20, y, MW - 40,
        "Se abre la ventana principal con el menú según el rol (ADMIN o ENCARGADO)", true);

    link(g, inicio, "bottom", .5, ventana, "top", .5);
    link(g, ventana, "bottom", .5, hayUsuarios, "top", .5);
    link(g, hayUsuarios, "right", .5, alta, "left", .5, "No (primer arranque)", 0);
    link(g, alta, "bottom", .5, altaRostro, "top", .5);
    link(g, altaRostro, "left", .5, ventana, "right", .5, "vuelve al login", 0);
    link(g, hayUsuarios, "bottom", .5, credenciales, "top", .5, "Sí", 0);
    link(g, credenciales, "right", .5, err1, "left", .5, "No", 0);
    link(g, err1, "bottom", .5, aud1, "top", .5);
    link(g, aud1, "bottom", .5, fin1, "top", .5);
    link(g, credenciales, "bottom", .5, llama, "top", .5, "Sí", 0);
    link(g, llama, "bottom", .5, cam, "top", .5);
    link(g, cam, "bottom", .5, coincide, "top", .5);
    link(g, coincide, "right", .5, err2, "left", .5, "No / falla", 0);
    link(g, err2, "bottom", .5, aud2, "top", .5);
    link(g, aud2, "bottom", .5, fin2, "top", .5);
    link(g, coincide, "bottom", .5, audOk, "top", .5, "Sí", 0);
    link(g, audOk, "bottom", .5, finOk, "top", .5);

    g.dispose();
    ImageIO.write(img, "png", new File("docs/diagramas/flujo-login.png"));
  }

  // =========================================================================
  // 2. FLUJO DE PRÉSTAMO
  // =========================================================================
  static void flujoPrestamo() throws Exception {
    int W = 1180, H = 1900, MX = 90, MW = 470, SX = 680, SW = 430;
    BufferedImage img = canvas(W, H);
    Graphics2D g = img.createGraphics();
    heading(g, W, "SIGELAB · Flujo de préstamo de equipos",
        "Un préstamo puede incluir varios equipos; todos cambian de estado en una sola transacción");

    int y = 100;
    Box inicio = terminal(g, MX + 60, y, MW - 120,
        "Pestaña «Préstamos y devoluciones» → botón «Nuevo préstamo»", true);
    y = inicio.y() + inicio.h() + 30;
    Box datos = process(g, MX, y, MW,
        "Se escriben el solicitante del préstamo, la fecha de devolución esperada (siempre"
            + " posterior al día y hora actuales) y observaciones.", PROC_BG, PROC_FG);
    y = datos.y() + datos.h() + 26;
    Box agregar = process(g, MX, y, MW,
        "Se agrega cada equipo escribiendo su código de inventario (ej. CAM-001) o pulsando"
            + " «Leer QR de imagen».", PROC_BG, PROC_FG);
    y = agregar.y() + agregar.h() + 24;
    Box busca = process(g, MX, y, MW,
        "El sistema busca el equipo en SQL Server por su código. La lectura de QR SOLO identifica"
            + " al equipo: nunca cambia su estado.", DB_BG, DB_FG);
    y = busca.y() + busca.h() + 30;
    Box valido = decision(g, MX + MW / 2, y, 380, 160,
        "¿El equipo existe, está DISPONIBLE y no está repetido en la lista?");

    int ey = valido.y() - 20;
    Box errP = process(g, SX, ey, SW,
        "Mensaje de rechazo: «EQ-X está PRESTADO/DAÑADO/MANTENIMIENTO/BAJA y no puede prestarse»,"
            + " «no existe un equipo con ese código» o «ya fue agregado».", ERR_BG, ERR_FG);
    Box finP = terminal(g, SX + 30, errP.y() + errP.h() + 22, SW - 60,
        "Se quita el equipo o se corrige el código", false);

    y = valido.y() + valido.h() + 30;
    Box lista = process(g, MX, y, MW,
        "El equipo se agrega a la lista temporal del préstamo (se puede quitar antes de"
            + " confirmar).", PROC_BG, PROC_FG);
    y = lista.y() + lista.h() + 30;
    Box otro = decision(g, MX + MW / 2, y, 300, 130, "¿Se agrega otro equipo?");

    y = otro.y() + otro.h() + 34;
    Box tx = process(g, MX - 30, y, MW + 60,
        "TRANSACCIÓN única en SQL Server al confirmar:\n"
            + "1) Se bloquean los equipos (UPDLOCK)  2) Se verifica que sigan DISPONIBLES\n"
            + "3) Se inserta el encabezado Prestamo  4) Se inserta un DetallePrestamo por equipo\n"
            + "5) Cada equipo pasa a PRESTADO  6) Auditoría: PRESTAMO_CREADO  7) COMMIT",
        DB_BG, DB_FG);
    y = tx.y() + tx.h() + 30;
    Box ok = decision(g, MX + MW / 2, y, 320, 140, "¿Toda la transacción se completó sin errores?");

    int ry = ok.y() - 10;
    Box rollback = process(g, SX, ry, SW,
        "ROLLBACK total: no queda encabezado, ni detalles, ni equipos PRESTADOS, ni auditoría."
            + " El usuario ve un mensaje claro y el error técnico va al registro.", ERR_BG, ERR_FG);
    Box finR = terminal(g, SX + 30, rollback.y() + rollback.h() + 22, SW - 60,
        "Préstamo rechazado; nada quedó a medias", false);

    y = ok.y() + ok.h() + 30;
    Box finOk = terminal(g, MX + 30, y, MW - 60,
        "Mensaje «Préstamo #N creado»; los equipos quedan PRESTADOS", true);

    link(g, inicio, "bottom", .5, datos, "top", .5);
    link(g, datos, "bottom", .5, agregar, "top", .5);
    link(g, agregar, "bottom", .5, busca, "top", .5);
    link(g, busca, "bottom", .5, valido, "top", .5);
    link(g, valido, "right", .5, errP, "left", .5, "No", 0);
    link(g, errP, "bottom", .5, finP, "top", .5);
    link(g, valido, "bottom", .5, lista, "top", .5, "Sí", 0);
    link(g, lista, "bottom", .5, otro, "top", .5);
    link(g, otro, "left", .4, agregar, "left", .6, "Sí", 0);
    link(g, otro, "bottom", .5, tx, "top", .5, "No", 0);
    link(g, tx, "bottom", .5, ok, "top", .5);
    link(g, ok, "right", .5, rollback, "left", .5, "No", 0);
    link(g, rollback, "bottom", .5, finR, "top", .5);
    link(g, ok, "bottom", .5, finOk, "top", .5, "Sí", 0);

    g.dispose();
    ImageIO.write(img, "png", new File("docs/diagramas/flujo-prestamo.png"));
  }

  // =========================================================================
  // 3. FLUJO DE DEVOLUCIÓN
  // =========================================================================
  static void flujoDevolucion() throws Exception {
    int W = 1180, H = 1660, MX = 100, MW = 450, SX = 690, SW = 420;
    BufferedImage img = canvas(W, H);
    Graphics2D g = img.createGraphics();
    heading(g, W, "SIGELAB · Flujo de devolución de equipos",
        "Puede ser parcial (algunos equipos) o completa; el historial nunca se borra");

    int y = 100;
    Box inicio = terminal(g, MX + 50, y, MW - 100,
        "Se selecciona un préstamo → «Ver detalle / devolver»", true);
    y = inicio.y() + inicio.h() + 30;
    Box detalles = process(g, MX, y, MW,
        "El sistema muestra los equipos del préstamo y cuáles siguen pendientes (los que no"
            + " tienen fecha de devolución).", DB_BG, DB_FG);
    y = detalles.y() + detalles.h() + 30;
    Box activo = decision(g, MX + MW / 2, y, 320, 130, "¿El préstamo está ACTIVO?");

    int ey = activo.y() - 10;
    Box errA = process(g, SX, ey, SW,
        "Mensaje: «El préstamo no está activo». Solo se devuelven equipos de préstamos activos.",
        ERR_BG, ERR_FG);
    Box finA = terminal(g, SX + 30, errA.y() + errA.h() + 22, SW - 60, "Operación rechazada", false);

    y = activo.y() + activo.h() + 30;
    Box sel = process(g, MX, y, MW,
        "Se seleccionan uno o varios equipos pendientes (Ctrl+clic) y se pulsa «Devolver"
            + " seleccionados».", PROC_BG, PROC_FG);
    y = sel.y() + sel.h() + 24;
    Box cond = process(g, MX, y, MW,
        "Se elige la condición real del equipo: BUENO o DANADO, con observaciones del estado"
            + " en que regresó.", PROC_BG, PROC_FG);
    y = cond.y() + cond.h() + 28;
    Box tx = process(g, MX - 30, y, MW + 60,
        "TRANSACCIÓN por cada devolución:\n"
            + "1) Se bloquean el préstamo y los detalles  2) Se verifica que el equipo esté pendiente"
            + " (nadie lo devolvió antes)\n"
            + "3) Se inserta el registro Devolucion (condición, observaciones y quién recibe)\n"
            + "4) Se marca la fecha de devolución en el detalle\n"
            + "5) El equipo pasa a DISPONIBLE (si llegó BUENO) o a DANADO\n"
            + "6) Auditoría del movimiento", DB_BG, DB_FG);
    y = tx.y() + tx.h() + 30;
    Box doble = decision(g, MX + MW / 2, y, 340, 140,
        "¿Se intentó devolver algo ya devuelto o que no pertenece al préstamo?");

    int dy = doble.y() - 10;
    Box errD = process(g, SX, dy, SW,
        "Rechazo y ROLLBACK completo de esa devolución: no queda ninguna marca parcial. Un equipo"
            + " nunca se devuelve dos veces.", ERR_BG, ERR_FG);
    Box finD = terminal(g, SX + 30, errD.y() + errD.h() + 22, SW - 60, "Operación rechazada", false);

    y = doble.y() + doble.h() + 30;
    Box quedan = decision(g, MX + MW / 2, y, 340, 140, "¿Quedan equipos pendientes en el préstamo?");

    int py = quedan.y() - 40;
    Box parcial = terminal(g, SX, py, SW - 40,
        "Devolución PARCIAL: el préstamo sigue ACTIVO con los equipos restantes", true);

    y = quedan.y() + quedan.h() + 40;
    Box cierra = process(g, MX + 40, y, MW - 80,
        "El préstamo pasa a FINALIZADO (auditoría incluida)", DB_BG, DB_FG);
    y = cierra.y() + cierra.h() + 26;
    Box finOk = terminal(g, MX + 40, y, MW - 80, "Devolución completa registrada", true);

    link(g, inicio, "bottom", .5, detalles, "top", .5);
    link(g, detalles, "bottom", .5, activo, "top", .5);
    link(g, activo, "right", .5, errA, "left", .5, "No", 0);
    link(g, errA, "bottom", .5, finA, "top", .5);
    link(g, activo, "bottom", .5, sel, "top", .5, "Sí", 0);
    link(g, sel, "bottom", .5, cond, "top", .5);
    link(g, cond, "bottom", .5, tx, "top", .5);
    link(g, tx, "bottom", .5, doble, "top", .5);
    link(g, doble, "right", .5, errD, "left", .5, "Sí", 0);
    link(g, errD, "bottom", .5, finD, "top", .5);
    link(g, doble, "bottom", .5, quedan, "top", .5, "No", 0);
    link(g, quedan, "right", .5, parcial, "left", .5, "Sí", 0);
    link(g, quedan, "bottom", .5, cierra, "top", .5, "No", 0);
    link(g, cierra, "bottom", .5, finOk, "top", .5);

    g.dispose();
    ImageIO.write(img, "png", new File("docs/diagramas/flujo-devolucion.png"));
  }

  // =========================================================================
  // 4. FLUJO DE MANTENIMIENTO
  // =========================================================================
  static void flujoMantenimiento() throws Exception {
    int W = 1180, H = 1900, MX = 100, MW = 450, SX = 690, SW = 420;
    BufferedImage img = canvas(W, H);
    Graphics2D g = img.createGraphics();
    heading(g, W, "SIGELAB · Flujo de mantenimiento",
        "Un equipo dañado solo vuelve a estar disponible si pasa por mantenimiento");

    g.setFont(new Font("SansSerif", Font.BOLD, 17));
    g.setColor(new Color(0x1F3A5F));
    g.drawString("PARTE A · Enviar equipo a mantenimiento", MX, 104);
    int y = 122;
    Box inicio = terminal(g, MX + 60, y, MW - 100,
        "Pestaña «Mantenimiento» → botón «Enviar equipo»", true);
    y = inicio.y() + inicio.h() + 26;
    Box form = process(g, MX, y, MW,
        "Se eligen el equipo, el tipo de mantenimiento (PREVENTIVO o CORRECTIVO), la descripción"
            + " del trabajo y el responsable técnico.", PROC_BG, PROC_FG);
    y = form.y() + form.h() + 30;
    Box estado = decision(g, MX + MW / 2, y, 360, 150,
        "¿El equipo está DISPONIBLE o DANADO?");

    int ey = estado.y() - 10;
    Box errM = process(g, SX, ey, SW,
        "Mensaje: solo pueden ir a mantenimiento los equipos disponibles o dañados. Un equipo"
            + " PRESTADO, en MANTENIMIENTO o de BAJA se rechaza.", ERR_BG, ERR_FG);
    Box finM = terminal(g, SX + 30, errM.y() + errM.h() + 22, SW - 60, "Operación rechazada", false);

    y = estado.y() + estado.h() + 30;
    Box tx1 = process(g, MX - 20, y, MW + 40,
        "TRANSACCIÓN: se crea el Mantenimiento en estado ABIERTO, el equipo pasa a MANTENIMIENTO"
            + " y se audita EQUIPO_ENVIADO_MANTENIMIENTO. Sugerencia: DISPONIBLE entra como"
            + " PREVENTIVO y DANADO como CORRECTIVO.", DB_BG, DB_FG);
    y = tx1.y() + tx1.h() + 44;

    g.setFont(new Font("SansSerif", Font.BOLD, 17));
    g.setColor(new Color(0x1F3A5F));
    g.drawString("PARTE B · Finalizar mantenimiento", MX, y + 4);
    y += 22;
    Box inicio2 = terminal(g, MX + 60, y, MW - 100,
        "Con la revisión terminada → botón «Finalizar»", true);
    y = inicio2.y() + inicio2.h() + 26;
    Box res = decision(g, MX + MW / 2, y, 320, 130, "¿Cuál es el resultado de la revisión?");

    int ly = res.y() + res.h() + 26;
    Box rep = process(g, MX - 60, ly, 360,
        "REPARADO: el equipo vuelve a DISPONIBLE y ya puede prestarse de nuevo.", OK_BG, OK_FG);
    Box baja = process(g, SX + 40, ly, 360,
        "NO_REPARABLE: el equipo pasa a BAJA. Es el final de su vida útil: nunca vuelve a"
            + " prestarse.", ERR_BG, ERR_FG);

    y = Math.max(rep.y() + rep.h(), baja.y() + baja.h()) + 26;
    Box tx2 = process(g, MX - 20, y, MW + 40,
        "TRANSACCIÓN: el mantenimiento queda FINALIZADO con fecha de salida y resultado;"
            + " auditoría: MANTENIMIENTO_FINALIZADO. La base impide dos mantenimientos abiertos"
            + " del mismo equipo.", DB_BG, DB_FG);
    y = tx2.y() + tx2.h() + 26;
    Box fin = terminal(g, MX + 80, y, MW - 160, "Fin del mantenimiento", true);

    link(g, inicio, "bottom", .5, form, "top", .5);
    link(g, form, "bottom", .5, estado, "top", .5);
    link(g, estado, "right", .5, errM, "left", .5, "No", 0);
    link(g, errM, "bottom", .5, finM, "top", .5);
    link(g, estado, "bottom", .5, tx1, "top", .5, "Sí", 0);
    link(g, tx1, "bottom", .5, inicio2, "top", .5, "días después…", 0);
    link(g, inicio2, "bottom", .5, res, "top", .5);
    link(g, res, "bottom", .25, rep, "top", .5, "REPARADO", 0);
    link(g, res, "bottom", .75, baja, "top", .5, "NO_REPARABLE", 0);
    link(g, rep, "bottom", .5, tx2, "top", .25);
    link(g, baja, "bottom", .5, tx2, "top", .75);
    link(g, tx2, "bottom", .5, fin, "top", .5);

    g.dispose();
    ImageIO.write(img, "png", new File("docs/diagramas/flujo-mantenimiento.png"));
  }

  // =========================================================================
  // 5. MÁQUINA DE ESTADOS DEL EQUIPO
  // =========================================================================
  static void estadosEquipo() throws Exception {
    int W = 1420, H = 1050;
    BufferedImage img = canvas(W, H);
    Graphics2D g = img.createGraphics();
    heading(g, W, "SIGELAB · Ciclo de vida del equipo (máquina de estados)",
        "El estado nunca se edita a mano: solo cambian los procesos de préstamo, devolución y mantenimiento");

    g.setColor(ARROW);
    g.fillOval(90, 138, 18, 18);
    g.setFont(new Font("SansSerif", Font.BOLD, 13));
    g.setColor(INK);
    g.drawString("alta de equipo nuevo (siempre nace DISPONIBLE)", 60, 128);

    Box disp = terminal(g, 560, 110, 280, 76, "DISPONIBLE", true);
    Box prest = terminal(g, 120, 400, 250, 76, "PRESTADO", true);
    Box dan = terminal(g, 570, 410, 250, 76, "DANADO", false);
    Box mant = terminal(g, 1000, 405, 300, 76, "MANTENIMIENTO", true);
    Box baja = terminal(g, 1030, 740, 250, 76, "BAJA", false);

    link(g, new Box(108, 147, 1, 1), "right", .5, disp, "left", .5, "registrar equipo", 0);
    link(g, disp, "bottom", .2, prest, "top", .5, "confirmar préstamo", 0);
    link(g, prest, "top", .8, disp, "bottom", .55, "devolución BUENO", 0);
    link(g, prest, "right", .5, dan, "left", .5, "devolución DAÑADO", 0);
    link(g, dan, "right", .5, mant, "left", .5, "mantenimiento CORRECTIVO", 0);
    link(g, disp, "right", .35, mant, "top", .15, "mantenimiento PREVENTIVO", 0);
    link(g, mant, "top", .7, disp, "bottom", .82, "finaliza REPARADO", 0);
    link(g, mant, "bottom", .6, baja, "top", .5, "finaliza NO_REPARABLE", 0);

    note(g, 90, 640, 470,
        "Reglas clave para la defensa:\n"
            + "· Solo un equipo DISPONIBLE puede prestarse; cualquier otro estado se rechaza.\n"
            + "· Un equipo devuelto como DAÑADO no se presta: obligatoriamente pasa por mantenimiento"
            + " antes de volver a DISPONIBLE.\n"
            + "· BAJA es el único estado final (no sale de ahí).\n"
            + "· El formulario de equipos muestra el estado en modo solo lectura.");

    g.dispose();
    ImageIO.write(img, "png", new File("docs/diagramas/estados-equipo.png"));
  }

  // =========================================================================
  // 6. CASOS DE USO
  // =========================================================================
  static Box ellipseUc(Graphics2D g, int cx, int cy, int w, int h, String text) {
    g.setFont(new Font("SansSerif", Font.PLAIN, 14));
    String[] lines = wrap(text, (int) (w * 0.72 / 7));
    g.setColor(new Color(0xE3F2FD));
    g.fillOval(cx - w / 2, cy - h / 2, w, h);
    g.setColor(new Color(0x1565C0));
    g.setStroke(new BasicStroke(1.6f));
    g.drawOval(cx - w / 2, cy - h / 2, w, h);
    g.setColor(new Color(0x0D47A1));
    FontMetrics fm = g.getFontMetrics();
    int lineH = 19;
    int y0 = cy - (lines.length - 1) * lineH / 2;
    for (int i = 0; i < lines.length; i++)
      g.drawString(lines[i], cx - fm.stringWidth(lines[i]) / 2, y0 + lineH * i);
    return new Box(cx - w / 2, cy - h / 2, w, h);
  }

  static Box actor(Graphics2D g, int cx, int top, String name) {
    g.setColor(INK);
    g.setStroke(new BasicStroke(2.2f));
    g.drawOval(cx - 14, top, 28, 28);            // cabeza
    g.drawLine(cx, top + 28, cx, top + 74);      // cuerpo
    g.drawLine(cx - 26, top + 44, cx + 26, top + 44); // brazos
    g.drawLine(cx, top + 74, cx - 22, top + 104);     // piernas
    g.drawLine(cx, top + 74, cx + 22, top + 104);
    g.setFont(new Font("SansSerif", Font.BOLD, 15));
    FontMetrics fm = g.getFontMetrics();
    g.drawString(name, cx - fm.stringWidth(name) / 2, top + 126);
    return new Box(cx - 30, top, 60, 132);
  }

  static void casosUso() throws Exception {
    int W = 1560, H = 1400;
    BufferedImage img = canvas(W, H);
    Graphics2D g = img.createGraphics();
    heading(g, W, "SIGELAB · Diagrama de casos de uso",
        "ADMIN hereda todos los casos de uso de ENCARGADO; el servicio facial es un sistema externo");

    // Límite del sistema
    g.setColor(new Color(0x1F3A5F));
    g.setStroke(new BasicStroke(2f));
    g.drawRoundRect(360, 96, 880, 1090, 18, 18);
    g.setFont(new Font("SansSerif", Font.BOLD, 16));
    g.drawString("SIGELAB · Sistema de gestión de laboratorios", 390, 124);

    // Columna izquierda (principalmente ADMIN)
    Box ucLogin = ellipseUc(g, 585, 210, 260, 84, "Iniciar sesión (con verificación facial)");
    Box ucLogout = ellipseUc(g, 585, 330, 240, 76, "Cerrar sesión");
    Box ucUsuarios = ellipseUc(g, 585, 445, 260, 84, "Gestionar usuarios y roles");
    Box ucRostro = ellipseUc(g, 585, 560, 260, 84, "Registrar o reemplazar rostro");
    Box ucLabs = ellipseUc(g, 585, 675, 260, 84, "Gestionar laboratorios");
    Box ucTipos = ellipseUc(g, 585, 790, 260, 84, "Gestionar tipos de equipo");
    Box ucAudit = ellipseUc(g, 585, 905, 260, 84, "Consultar auditoría");
    Box ucRest = ellipseUc(g, 585, 1020, 260, 84, "Restablecer contraseña (utilidad)");

    // Columna derecha (operación: ENCARGADO y ADMIN)
    Box ucEquipos = ellipseUc(g, 1030, 210, 280, 90, "Gestionar equipos y generar / leer su QR");
    Box ucPrestamo = ellipseUc(g, 1030, 335, 280, 90, "Registrar préstamo de varios equipos");
    Box ucDevol = ellipseUc(g, 1030, 460, 280, 90, "Registrar devolución parcial o total");
    Box ucMant1 = ellipseUc(g, 1030, 585, 280, 90, "Enviar equipo a mantenimiento");
    Box ucMant2 = ellipseUc(g, 1030, 710, 280, 90, "Finalizar mantenimiento");
    Box ucDash = ellipseUc(g, 1030, 835, 280, 90, "Ver dashboard de indicadores");
    Box ucRep = ellipseUc(g, 1030, 960, 280, 90, "Consultar reportes e historiales");
    Box ucQr = ellipseUc(g, 1030, 1085, 280, 90, "Identificar equipo leyendo su QR");

    // Actores
    Box admin = actor(g, 175, 400, "ADMIN");
    Box encargado = actor(g, 1385, 400, "ENCARGADO");
    Box facial = actor(g, 175, 1020, "Servicio facial Python (externo)");

    // ADMIN -> columna izquierda
    link(g, admin, "right", .18, ucLogin, "left", .5);
    link(g, admin, "right", .3, ucLogout, "left", .5);
    link(g, admin, "right", .42, ucUsuarios, "left", .5);
    link(g, admin, "right", .55, ucRostro, "left", .5);
    link(g, admin, "right", .68, ucLabs, "left", .5);
    link(g, admin, "right", .8, ucTipos, "left", .5);
    link(g, admin, "right", .95, ucAudit, "left", .5);
    link(g, admin, "bottom", .5, ucRest, "left", .3);

    // ENCARGADO -> columna derecha
    link(g, encargado, "left", .15, ucEquipos, "right", .5);
    link(g, encargado, "left", .27, ucPrestamo, "right", .5);
    link(g, encargado, "left", .4, ucDevol, "right", .5);
    link(g, encargado, "left", .53, ucMant1, "right", .5);
    link(g, encargado, "left", .66, ucMant2, "right", .5);
    link(g, encargado, "left", .79, ucDash, "right", .5);
    link(g, encargado, "left", .95, ucRep, "right", .5);
    link(g, encargado, "bottom", .5, ucQr, "right", .3);

    // Generalización ADMIN --|> ENCARGADO (hereda sus casos de uso)
    link(g, admin, "left", .5, encargado, "left", .5, "ADMIN hereda los casos de uso de ENCARGADO", 2);

    // Servicio facial conectado con los casos que le llegan por HTTP
    link(g, facial, "right", .2, ucRostro, "bottom", .4, "enroll / verify (HTTP local)", 1);
    link(g, facial, "top", .5, ucLogin, "left", .35, "verify (HTTP local)", 1);

    // QR incluye
    link(g, ucPrestamo, "bottom", .75, ucQr, "top", .5, "«incluye» lectura de QR", 1);
    link(g, ucEquipos, "bottom", .75, ucQr, "top", .8, "«incluye»", 1);

    note(g, 620, 1160, 600,
        "Lectura del diagrama: cada línea une a un actor con lo que puede hacer dentro del"
            + " sistema. La flecha con triángulo hueco indica que ADMIN hereda todo lo de"
            + " ENCARGADO. Las flechas punteadas conectan con el componente de reconocimiento"
            + " facial de Python, que corre aparte y solo responde en 127.0.0.1 con token.");

    g.dispose();
    ImageIO.write(img, "png", new File("docs/diagramas/casos-uso.png"));
  }

  // =========================================================================
  // 7. MODELO ENTIDAD-RELACIÓN
  // =========================================================================
  record ColEr(String nombre, String tipo, String clave) {}

  record TablaEr(String nombre, List<ColEr> cols, int x, int y) {}

  record RelEr(TablaEr a, TablaEr b, String cardA, String cardB, String verbo) {}

  static int erWidth(Graphics2D g, TablaEr t) {
    FontMetrics fh = g.getFontMetrics(new Font("SansSerif", Font.BOLD, 17));
    FontMetrics fr = g.getFontMetrics(new Font("SansSerif", Font.PLAIN, 14));
    int max = fh.stringWidth(t.nombre());
    for (ColEr c : t.cols())
      max = Math.max(max, fr.stringWidth(" " + c.nombre() + " : " + c.tipo()
          + (c.clave().isEmpty() ? "" : "  " + c.clave())));
    return max + 26;
  }

  static Box erDraw(Graphics2D g, TablaEr t) {
    int rowH = 22, headerH = 30;
    int w = erWidth(g, t);
    int h = headerH + t.cols().size() * rowH;
    g.setColor(ER_HEAD);
    g.fillRect(t.x(), t.y(), w, headerH);
    g.setColor(ER_ROW);
    g.fillRect(t.x(), t.y() + headerH, w, h - headerH);
    g.setColor(ER_BOR);
    g.setStroke(new BasicStroke(1.4f));
    g.drawRect(t.x(), t.y(), w, h);
    g.setColor(Color.WHITE);
    g.setFont(new Font("SansSerif", Font.BOLD, 17));
    g.drawString(" " + t.nombre(), t.x() + 6, t.y() + 22);
    int y = t.y() + headerH;
    g.setFont(new Font("SansSerif", Font.PLAIN, 14));
    for (ColEr c : t.cols()) {
      boolean clave = !c.clave().isEmpty();
      g.setFont(clave ? new Font("SansSerif", Font.BOLD, 14)
                      : new Font("SansSerif", Font.PLAIN, 14));
      g.setColor(c.clave().startsWith("FK") ? ER_FK : new Color(0x222222));
      g.drawString(" " + c.nombre() + " : " + c.tipo()
          + (clave ? "  " + c.clave() : ""), t.x() + 6, y + 16);
      y += rowH;
    }
    return new Box(t.x(), t.y(), w, h);
  }

  static void modeloEr() throws Exception {
    List<ColEr> rol = List.of(new ColEr("Id", "INT", "PK"),
        new ColEr("Nombre", "VARCHAR(20)", "UQ"));
    List<ColEr> usuario = List.of(new ColEr("Id", "INT", "PK"),
        new ColEr("Nombre", "NVARCHAR(80)", ""), new ColEr("Apellido", "NVARCHAR(80)", ""),
        new ColEr("NombreUsuario", "NVARCHAR(50)", "UQ"),
        new ColEr("PasswordHash", "VARCHAR(512)", "BCrypt"),
        new ColEr("Correo", "NVARCHAR(254)", "UQ"), new ColEr("RolId", "INT", "FK"),
        new ColEr("Estado", "VARCHAR(10)", "ACT/INACT"));
    List<ColEr> lab = List.of(new ColEr("Id", "INT", "PK"),
        new ColEr("Codigo", "NVARCHAR(30)", "UQ"), new ColEr("Nombre", "NVARCHAR(100)", ""),
        new ColEr("Ubicacion", "NVARCHAR(150)", ""), new ColEr("Capacidad", "INT", "> 0"),
        new ColEr("Estado", "VARCHAR(10)", ""));
    List<ColEr> tipo = List.of(new ColEr("Id", "INT", "PK"),
        new ColEr("Nombre", "NVARCHAR(80)", "UQ"), new ColEr("Estado", "VARCHAR(10)", ""));
    List<ColEr> equipo = List.of(new ColEr("Id", "INT", "PK"),
        new ColEr("CodigoInventario", "NVARCHAR(30)", "UQ"),
        new ColEr("Nombre", "NVARCHAR(100)", ""), new ColEr("TipoEquipoId", "INT", "FK"),
        new ColEr("Marca", "NVARCHAR(80)", ""), new ColEr("Modelo", "NVARCHAR(80)", ""),
        new ColEr("NumeroSerie", "NVARCHAR(100)", "UQ"),
        new ColEr("LaboratorioId", "INT", "FK"), new ColEr("Estado", "VARCHAR(15)", "5 estados"),
        new ColEr("Observaciones", "NVARCHAR(1000)", "NULL"));
    List<ColEr> prestamo = List.of(new ColEr("Id", "INT", "PK"),
        new ColEr("Solicitante", "NVARCHAR(150)", ""),
        new ColEr("FechaPrestamo", "DATETIME2", ""),
        new ColEr("FechaEsperada", "DATETIME2", "> préstamo"),
        new ColEr("UsuarioRegistraId", "INT", "FK"),
        new ColEr("Estado", "VARCHAR(12)", "ACT/FIN"),
        new ColEr("Observaciones", "NVARCHAR(1000)", "NULL"));
    List<ColEr> detalle = List.of(new ColEr("Id", "INT", "PK"),
        new ColEr("PrestamoId", "INT", "FK"), new ColEr("EquipoId", "INT", "FK"),
        new ColEr("FechaDevolucion", "DATETIME2", "NULL = pendiente"));
    List<ColEr> devolucion = List.of(new ColEr("Id", "INT", "PK"),
        new ColEr("DetallePrestamoId", "INT", "FK UQ"), new ColEr("Fecha", "DATETIME2", ""),
        new ColEr("Condicion", "VARCHAR(10)", "BUENO/DANADO"),
        new ColEr("Observaciones", "NVARCHAR(1000)", "NULL"),
        new ColEr("UsuarioRegistraId", "INT", "FK"));
    List<ColEr> mantenimiento = List.of(new ColEr("Id", "INT", "PK"),
        new ColEr("EquipoId", "INT", "FK"), new ColEr("FechaIngreso", "DATETIME2", ""),
        new ColEr("Tipo", "VARCHAR(12)", "PREV/CORR"),
        new ColEr("Descripcion", "NVARCHAR(1000)", ""),
        new ColEr("Responsable", "NVARCHAR(150)", ""),
        new ColEr("FechaSalida", "DATETIME2", "NULL"),
        new ColEr("Resultado", "VARCHAR(15)", "REPARADO/NO"),
        new ColEr("Estado", "VARCHAR(12)", "ABIERTO/FIN"),
        new ColEr("Observaciones", "NVARCHAR(1000)", "NULL"),
        new ColEr("UsuarioRegistraId", "INT", "FK"));
    List<ColEr> auditoria = List.of(new ColEr("Id", "BIGINT", "PK"),
        new ColEr("Fecha", "DATETIME2", ""), new ColEr("UsuarioId", "INT", "FK NULL"),
        new ColEr("Accion", "VARCHAR(60)", ""), new ColEr("Entidad", "VARCHAR(60)", ""),
        new ColEr("EntidadId", "INT", "NULL"), new ColEr("Detalle", "NVARCHAR(1000)", "NULL"));
    List<ColEr> perfil = List.of(new ColEr("id_perfil_facial", "INT", "PK"),
        new ColEr("id_usuario", "INT", "FK UQ"), new ColEr("embedding", "VARBINARY", "cifrado"),
        new ColEr("modelo", "NVARCHAR(100)", ""), new ColEr("dimension", "INT", ""),
        new ColEr("fecha_registro", "DATETIME2", ""),
        new ColEr("fecha_actualizacion", "DATETIME2", ""), new ColEr("activo", "BIT", ""),
        new ColEr("cantidad_muestras", "INT", ""), new ColEr("quality_score", "FLOAT", "NULL"),
        new ColEr("version_algoritmo", "NVARCHAR(50)", ""));
    List<ColEr> auditFacial = List.of(new ColEr("id_auditoria", "BIGINT", "PK"),
        new ColEr("id_usuario", "INT", "FK NULL"), new ColEr("fecha_hora", "DATETIME2", ""),
        new ColEr("accion", "VARCHAR(30)", ""), new ColEr("resultado", "VARCHAR(20)", ""),
        new ColEr("score", "FLOAT", "NULL"), new ColEr("threshold", "FLOAT", "NULL"),
        new ColEr("detalle", "NVARCHAR(1000)", "NULL"),
        new ColEr("dispositivo", "NVARCHAR(200)", "NULL"));

    TablaEr tRol = new TablaEr("Rol", rol, 60, 100);
    TablaEr tUsuario = new TablaEr("Usuario", usuario, 480, 100);
    TablaEr tPerfil = new TablaEr("PerfilFacial", perfil, 1420, 100);
    TablaEr tLab = new TablaEr("Laboratorio", lab, 60, 420);
    TablaEr tEquipo = new TablaEr("Equipo", equipo, 480, 430);
    TablaEr tAuditFacial = new TablaEr("AuditoriaFacial", auditFacial, 1420, 500);
    TablaEr tTipo = new TablaEr("TipoEquipo", tipo, 60, 830);
    TablaEr tMant = new TablaEr("Mantenimiento", mantenimiento, 1020, 800);
    TablaEr tPrestamo = new TablaEr("Prestamo", prestamo, 480, 980);
    TablaEr tDetalle = new TablaEr("DetallePrestamo", detalle, 1020, 1130);
    TablaEr tDevolucion = new TablaEr("Devolucion", devolucion, 1440, 1040);
    TablaEr tAuditoria = new TablaEr("Auditoria", auditoria, 60, 1150);

    List<TablaEr> tablas = List.of(tRol, tUsuario, tPerfil, tLab, tEquipo, tAuditFacial, tTipo,
        tMant, tPrestamo, tDetalle, tDevolucion, tAuditoria);
    List<RelEr> rels = List.of(
        new RelEr(tRol, tUsuario, "1", "N", "clasifica"),
        new RelEr(tUsuario, tPerfil, "1", "0..1", "posee"),
        new RelEr(tUsuario, tAuditFacial, "0..1", "N", "verifica"),
        new RelEr(tLab, tEquipo, "1", "N", "ubica"),
        new RelEr(tTipo, tEquipo, "1", "N", "categoriza"),
        new RelEr(tUsuario, tPrestamo, "1", "N", "registra"),
        new RelEr(tPrestamo, tDetalle, "1", "N", "incluye"),
        new RelEr(tEquipo, tDetalle, "1", "N", "participa"),
        new RelEr(tDetalle, tDevolucion, "1", "0..1", "se cierra con"),
        new RelEr(tUsuario, tDevolucion, "1", "N", "recibe"),
        new RelEr(tEquipo, tMant, "1", "N", "recibe"),
        new RelEr(tUsuario, tMant, "1", "N", "registra"),
        new RelEr(tUsuario, tAuditoria, "0..1", "N", "genera"));

    int W = 1980, H = 1560;
    BufferedImage img = canvas(W, H);
    Graphics2D g = img.createGraphics();
    heading(g, W, "SIGELAB · Modelo entidad-relación",
        "12 tablas: 10 funcionales + 2 del componente facial · coincide con database/02_esquema.sql y 05_facial.sql");

    // Primero las relaciones (quedan detrás), después las cajas.
    List<Box> cajas = new ArrayList<>();
    g.setFont(new Font("SansSerif", Font.PLAIN, 14));
    for (TablaEr t : tablas) {
      int w = erWidth(g, t);
      cajas.add(new Box(t.x(), t.y(), w, 30 + t.cols().size() * 22));
    }
    for (RelEr r : rels) {
      Box a = cajas.get(tablas.indexOf(r.a())), b = cajas.get(tablas.indexOf(r.b()));
      g.setColor(new Color(0x5B7DA8));
      g.setStroke(new BasicStroke(1.6f));
      g.drawLine(a.cx(), a.cy(), b.cx(), b.cy());
      g.setFont(new Font("SansSerif", Font.BOLD, 13));
      g.setColor(new Color(0x1F3A5F));
      g.drawString(r.cardA(), a.cx() + 8, a.cy() - 6);
      g.drawString(r.cardB(), b.cx() + 8, b.cy() - 6);
      g.setFont(new Font("SansSerif", Font.ITALIC, 13));
      g.setColor(new Color(0x4A6B8A));
      g.drawString(r.verbo(), (a.cx() + b.cx()) / 2 + 5, (a.cy() + b.cy()) / 2 - 5);
    }
    for (TablaEr t : tablas) erDraw(g, t);

    g.setFont(new Font("SansSerif", Font.PLAIN, 14));
    g.setColor(Color.GRAY);
    g.drawString("PK = primaria · UQ = única · FK = foránea · NULL = opcional · Sin borrado en cascada.",
        40, H - 40);

    g.dispose();
    ImageIO.write(img, "png", new File("docs/diagramas/modelo-er.png"));
  }

  public static void main(String[] args) throws Exception {
    new File("docs/diagramas").mkdirs();
    flujoLogin();
    flujoPrestamo();
    flujoDevolucion();
    flujoMantenimiento();
    estadosEquipo();
    casosUso();
    modeloEr();
    System.out.println("Diagramas generados en docs/diagramas/");
  }
}
