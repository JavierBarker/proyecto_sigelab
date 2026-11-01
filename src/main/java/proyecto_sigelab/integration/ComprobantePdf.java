package proyecto_sigelab.integration;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.ImageIO;
import proyecto_sigelab.exception.BusinessException;
import proyecto_sigelab.model.Comprobante;

/**
 * Genera el comprobante en PDF sin librerías externas: escribe directamente el formato PDF con
 * las fuentes estándar Helvetica y el escudo de la UMG incrustado como imagen.
 */
public final class ComprobantePdf {
  private static final Charset WIN_ANSI = Charset.forName("windows-1252");
  private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

  // Colores institucionales (los mismos de Theme).
  private static final Color AZUL = new Color(0x0F3D63);
  private static final Color CORAL = new Color(0xE1705D);
  private static final Color TURQUESA = new Color(0x2B8A9E);
  private static final Color TEXTO = new Color(0x1F2937);
  private static final Color GRIS = new Color(0x6B7280);
  private static final Color FILA = new Color(0xEEF4FA);
  private static final Color LINEA = new Color(0xDDE3EA);
  private static final Color CLARO = new Color(0xDCE8F3);

  // Página tamaño carta (612 x 792 puntos) y margen lateral.
  private static final float ANCHO = 612, ALTO = 792, MARGEN = 48;

  // Arial tiene las mismas medidas que Helvetica; sirve para calcular anchos de texto.
  private static final java.awt.Font REGULAR = new java.awt.Font("Arial", java.awt.Font.PLAIN, 1000);
  private static final java.awt.Font NEGRITA = new java.awt.Font("Arial", java.awt.Font.BOLD, 1000);
  private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

  private final ByteArrayOutputStream contenido = new ByteArrayOutputStream();
  private float y;

  private ComprobantePdf() {}

  /** Crea el PDF dentro de la carpeta indicada y devuelve la ruta del archivo. */
  public static Path generate(Comprobante c, Path carpeta) {
    try {
      Files.createDirectories(carpeta);
      String nombre = "Comprobante_" + c.folio().replaceAll("[^A-Za-z0-9_-]", "_") + ".pdf";
      Path archivo = carpeta.resolve(nombre);
      Files.write(archivo, new ComprobantePdf().build(c));
      return archivo;
    } catch (IOException e) {
      throw new BusinessException("No se pudo crear el comprobante PDF: " + e.getMessage());
    }
  }

  private byte[] build(Comprobante c) throws IOException {
    BufferedImage logo = logo();

    // Encabezado azul con el escudo y franja coral.
    rect(AZUL, 0, ALTO - 104, ANCHO, 104);
    rect(CORAL, 0, ALTO - 108, ANCHO, 4);
    float tx = MARGEN;
    if (logo != null) {
      image(MARGEN, ALTO - 94, 84, 84);
      tx = MARGEN + 100;
    }
    text("UNIVERSIDAD MARIANO GÁLVEZ DE GUATEMALA", tx, ALTO - 44, 14, true, Color.WHITE);
    text("SIGELAB · Sistema de Gestión de Laboratorios", tx, ALTO - 63, 11, false, CLARO);
    text("Comprobante de movimiento de equipo", tx, ALTO - 80, 9, false, CLARO);

    // Título, folio y fecha.
    y = ALTO - 152;
    centered(c.titulo().toUpperCase(Locale.ROOT), ANCHO / 2, y, 19, true, CORAL);
    y -= 28;
    text("Folio: " + c.folio(), MARGEN, y, 10.5f, true, TEXTO);
    String fecha = "Fecha: " + FECHA.format(c.fecha());
    text(fecha, ANCHO - MARGEN - width(fecha, 10.5f, false), y, 10.5f, false, TEXTO);
    y -= 10;
    line(LINEA, MARGEN, y, ANCHO - MARGEN, y, 1);

    section("Datos del equipo", c.equipo());
    section("Detalle del movimiento", c.detalle());

    // Firmas.
    float fy = Math.max(Math.min(y - 80, 180), 105);
    line(GRIS, 80, fy, 260, fy, 0.8f);
    line(GRIS, ANCHO - 260, fy, ANCHO - 80, fy, 0.8f);
    centered("Entregado por", 170, fy - 14, 9.5f, true, TEXTO);
    centered(c.responsable(), 170, fy - 28, 8.5f, false, GRIS);
    centered("Recibido por", ANCHO - 170, fy - 14, 9.5f, true, TEXTO);
    centered("Nombre y firma", ANCHO - 170, fy - 28, 8.5f, false, GRIS);

    // Pie de página.
    line(LINEA, MARGEN, 62, ANCHO - MARGEN, 62, 0.8f);
    centered(
        "Generado por SIGELAB el " + FECHA.format(c.fecha())
            + ". Comprobante interno de control; no constituye una factura fiscal.",
        ANCHO / 2,
        48,
        8,
        false,
        GRIS);

    return assemble(logo);
  }

  private void section(String titulo, List<Map.Entry<String, String>> filas) {
    y -= 30;
    text(titulo, MARGEN, y, 12, true, TURQUESA);
    y -= 7;
    line(TURQUESA, MARGEN, y, ANCHO - MARGEN, y, 1.2f);
    boolean sombra = false;
    for (Map.Entry<String, String> fila : filas) {
      List<String> lineas = wrap(fila.getValue() == null ? "" : fila.getValue(), 330, 10);
      float alto = lineas.size() * 13 + 9;
      if (sombra) rect(FILA, MARGEN, y - alto, ANCHO - 2 * MARGEN, alto);
      float ty = y - 15;
      text(fila.getKey(), MARGEN + 10, ty, 10, true, TEXTO);
      for (String l : lineas) {
        text(l, MARGEN + 180, ty, 10, false, TEXTO);
        ty -= 13;
      }
      y -= alto;
      sombra = !sombra;
    }
  }

  // ---------- Dibujo ----------

  private void text(String s, float x, float y, float size, boolean bold, Color c) {
    color(c, false);
    write("BT /" + (bold ? "F2" : "F1") + " " + n(size) + " Tf " + n(x) + " " + n(y) + " Td ");
    pdfString(s);
    write(" Tj ET\n");
  }

  private void centered(String s, float cx, float y, float size, boolean bold, Color c) {
    text(s, cx - width(s, size, bold) / 2, y, size, bold, c);
  }

  private void rect(Color c, float x, float y, float w, float h) {
    color(c, false);
    write(n(x) + " " + n(y) + " " + n(w) + " " + n(h) + " re f\n");
  }

  private void line(Color c, float x1, float y1, float x2, float y2, float w) {
    color(c, true);
    write(n(w) + " w " + n(x1) + " " + n(y1) + " m " + n(x2) + " " + n(y2) + " l S\n");
  }

  private void image(float x, float y, float w, float h) {
    write("q " + n(w) + " 0 0 " + n(h) + " " + n(x) + " " + n(y) + " cm /Im1 Do Q\n");
  }

  private void color(Color c, boolean stroke) {
    write(
        n(c.getRed() / 255f) + " " + n(c.getGreen() / 255f) + " " + n(c.getBlue() / 255f)
            + (stroke ? " RG\n" : " rg\n"));
  }

  private static float width(String s, float size, boolean bold) {
    if (s.isEmpty()) return 0;
    return (float) (bold ? NEGRITA : REGULAR).getStringBounds(s, FRC).getWidth() / 1000f * size;
  }

  private static List<String> wrap(String text, float max, float size) {
    List<String> lineas = new ArrayList<>();
    for (String parrafo : text.split("\\R")) {
      StringBuilder actual = new StringBuilder();
      for (String palabra : parrafo.trim().split("\\s+")) {
        // Parte palabras muy largas que no caben solas en una línea.
        while (width(palabra, size, false) > max && palabra.length() > 1) {
          int corte = palabra.length() - 1;
          while (corte > 1 && width(palabra.substring(0, corte), size, false) > max) corte--;
          if (actual.length() > 0) {
            lineas.add(actual.toString());
            actual.setLength(0);
          }
          lineas.add(palabra.substring(0, corte));
          palabra = palabra.substring(corte);
        }
        String prueba = actual.length() == 0 ? palabra : actual + " " + palabra;
        if (width(prueba, size, false) <= max) {
          actual.setLength(0);
          actual.append(prueba);
        } else {
          lineas.add(actual.toString());
          actual.setLength(0);
          actual.append(palabra);
        }
      }
      lineas.add(actual.toString());
    }
    return lineas;
  }

  private void pdfString(String s) {
    contenido.write('(');
    for (byte b : s.getBytes(WIN_ANSI)) {
      int v = b & 0xFF;
      if (v == '(' || v == ')' || v == '\\') {
        contenido.write('\\');
        contenido.write(v);
      } else if (v < 32 || v > 126) {
        write(String.format("\\%03o", v));
      } else {
        contenido.write(v);
      }
    }
    contenido.write(')');
  }

  private void write(String s) {
    contenido.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
  }

  private static String n(float v) {
    return String.format(Locale.ROOT, "%.2f", v);
  }

  // ---------- Escudo ----------

  private static BufferedImage logo() {
    try {
      URL url = ComprobantePdf.class.getResource("/img/logo_umg.png");
      if (url == null) return null;
      BufferedImage original = ImageIO.read(url);
      if (original == null) return null;
      int lado = 240;
      BufferedImage img = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(original, 0, 0, lado, lado, null);
      g.dispose();
      return img;
    } catch (IOException e) {
      return null;
    }
  }

  // ---------- Estructura del archivo PDF ----------

  private byte[] assemble(BufferedImage logo) throws IOException {
    List<byte[]> objetos = new ArrayList<>();
    String imagen = logo != null ? " /XObject << /Im1 7 0 R >>" : "";
    objetos.add(ascii("<< /Type /Catalog /Pages 2 0 R >>"));
    objetos.add(ascii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));
    objetos.add(
        ascii(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R"
                + " /F2 6 0 R >>" + imagen + " >> /Contents 4 0 R >>"));
    objetos.add(stream("/Filter /FlateDecode", deflate(contenido.toByteArray())));
    objetos.add(
        ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
    objetos.add(
        ascii(
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding"
                + " >>"));
    if (logo != null) {
      int w = logo.getWidth(), h = logo.getHeight();
      ByteArrayOutputStream rgb = new ByteArrayOutputStream(w * h * 3);
      ByteArrayOutputStream alfa = new ByteArrayOutputStream(w * h);
      for (int py = 0; py < h; py++)
        for (int px = 0; px < w; px++) {
          int p = logo.getRGB(px, py);
          rgb.write((p >> 16) & 0xFF);
          rgb.write((p >> 8) & 0xFF);
          rgb.write(p & 0xFF);
          alfa.write((p >>> 24) & 0xFF);
        }
      String tam = " /Width " + w + " /Height " + h + " /BitsPerComponent 8 /Filter /FlateDecode";
      objetos.add(
          stream(
              "/Type /XObject /Subtype /Image /ColorSpace /DeviceRGB /SMask 8 0 R" + tam,
              deflate(rgb.toByteArray())));
      objetos.add(
          stream("/Type /XObject /Subtype /Image /ColorSpace /DeviceGray" + tam, deflate(alfa.toByteArray())));
    }

    ByteArrayOutputStream pdf = new ByteArrayOutputStream();
    pdf.writeBytes("%PDF-1.4\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1));
    List<Integer> posiciones = new ArrayList<>();
    for (int i = 0; i < objetos.size(); i++) {
      posiciones.add(pdf.size());
      pdf.writeBytes(ascii((i + 1) + " 0 obj\n"));
      pdf.writeBytes(objetos.get(i));
      pdf.writeBytes(ascii("\nendobj\n"));
    }
    int xref = pdf.size();
    StringBuilder tabla = new StringBuilder("xref\n0 " + (objetos.size() + 1) + "\n0000000000 65535 f \n");
    for (int p : posiciones) tabla.append(String.format("%010d 00000 n \n", p));
    tabla.append("trailer\n<< /Size ").append(objetos.size() + 1).append(" /Root 1 0 R >>\n");
    tabla.append("startxref\n").append(xref).append("\n%%EOF\n");
    pdf.writeBytes(ascii(tabla.toString()));
    return pdf.toByteArray();
  }

  private static byte[] stream(String dict, byte[] data) {
    ByteArrayOutputStream o = new ByteArrayOutputStream();
    o.writeBytes(ascii("<< " + dict + " /Length " + data.length + " >>\nstream\n"));
    o.writeBytes(data);
    o.writeBytes(ascii("\nendstream"));
    return o.toByteArray();
  }

  private static byte[] deflate(byte[] data) throws IOException {
    ByteArrayOutputStream o = new ByteArrayOutputStream();
    try (DeflaterOutputStream d = new DeflaterOutputStream(o, new Deflater(Deflater.BEST_COMPRESSION))) {
      d.write(data);
    }
    return o.toByteArray();
  }

  private static byte[] ascii(String s) {
    return s.getBytes(StandardCharsets.US_ASCII);
  }
}
