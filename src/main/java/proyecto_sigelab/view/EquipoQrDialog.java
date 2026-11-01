package proyecto_sigelab.view;

import java.awt.*;
import java.net.URL;
import java.util.List;
import javax.swing.*;
import proyecto_sigelab.model.Tabla;

/**
 * Ventana que se abre al escanear el QR de un equipo: muestra el escudo de la UMG, los datos
 * del equipo, su historial de movimientos y los botones de las acciones permitidas según su
 * estado.
 */
public final class EquipoQrDialog extends JDialog {
  private final JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
  public final DataPanel historial = new DataPanel();

  /**
   * @param info fila con código, nombre, tipo, laboratorio y estado del equipo
   * @param movimientos historial unificado del equipo
   */
  public EquipoQrDialog(JFrame owner, List<Object> info, Tabla movimientos) {
    super(owner, "SIGELAB · Equipo " + info.get(0), false);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    Image logo = logo();
    if (logo != null) setIconImage(logo);

    // Encabezado: escudo, datos del equipo y estado.
    JPanel header = new JPanel(new BorderLayout(14, 0));
    header.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
    if (logo != null)
      header.add(new JLabel(new ImageIcon(logo.getScaledInstance(64, 64, Image.SCALE_SMOOTH))), BorderLayout.WEST);
    JPanel datos = new JPanel(new GridLayout(0, 1, 0, 2));
    JLabel titulo = new JLabel(info.get(0) + " · " + info.get(1));
    titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));
    datos.add(titulo);
    datos.add(new JLabel("Tipo: " + info.get(2) + "    Laboratorio: " + info.get(3)));
    JLabel estado = new JLabel("Estado: " + info.get(4));
    estado.setFont(estado.getFont().deriveFont(Font.BOLD));
    datos.add(estado);
    datos.add(new JLabel("Universidad Mariano Gálvez de Guatemala"));
    header.add(datos, BorderLayout.CENTER);

    acciones.setBorder(BorderFactory.createTitledBorder("Acciones disponibles"));

    JPanel norte = new JPanel(new BorderLayout(0, 6));
    norte.add(header, BorderLayout.NORTH);
    JPanel wrapAcciones = new JPanel(new BorderLayout());
    wrapAcciones.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
    wrapAcciones.add(acciones);
    norte.add(wrapAcciones, BorderLayout.CENTER);

    historial.display(movimientos);
    historial.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(6, 12, 12, 12),
            BorderFactory.createTitledBorder("Historial de movimientos")));

    JPanel root = new JPanel(new BorderLayout());
    root.add(norte, BorderLayout.NORTH);
    root.add(historial, BorderLayout.CENTER);
    setContentPane(root);
    setSize(900, 560);
    setMinimumSize(new Dimension(760, 460));
    setLocationRelativeTo(owner);
  }

  /** Agrega un botón de acción; la acción principal va en negrita. */
  public JButton action(String texto, boolean principal, Runnable accion) {
    JButton b = new JButton(texto);
    if (principal) b.setFont(b.getFont().deriveFont(Font.BOLD));
    b.addActionListener(e -> accion.run());
    acciones.add(b);
    return b;
  }

  public void note(String texto) {
    acciones.add(new JLabel(texto));
  }

  private static Image logo() {
    URL url = EquipoQrDialog.class.getResource("/img/logo_umg.png");
    return url == null ? null : new ImageIcon(url).getImage();
  }
}
