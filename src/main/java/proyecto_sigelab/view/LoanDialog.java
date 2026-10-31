package proyecto_sigelab.view;

import java.awt.*;
import java.time.LocalDateTime;
import java.util.*;
import javax.swing.*;
import proyecto_sigelab.model.Equipo;

public final class LoanDialog extends JDialog {
  public final JTextField person = new JTextField(24),
      due =
          new JTextField(
              LocalDateTime.now(java.time.ZoneId.of("America/Guatemala"))
                  .plusDays(1)
                  .withSecond(0)
                  .withNano(0)
                  .toString(),
              24),
      notes = new JTextField(24),
      code = new JTextField(18);
  public final JButton add = new JButton("Buscar y agregar"),
      qr = new JButton("Leer QR de imagen"),
      remove = new JButton("Quitar seleccionado"),
      confirm = new JButton("Confirmar préstamo");
  private final DefaultListModel<Equipo> model = new DefaultListModel<>();
  public final JList<Equipo> list = new JList<>(model);

  public LoanDialog(JFrame owner) {
    super(owner, "Nuevo préstamo", false);
    JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
    fields.add(new JLabel("Solicitante"));
    fields.add(person);
    fields.add(new JLabel("Devolución (AAAA-MM-DDTHH:mm)"));
    fields.add(due);
    fields.add(new JLabel("Observaciones"));
    fields.add(notes);
    fields.add(new JLabel("Código de inventario"));
    fields.add(code);
    fields.add(add);
    fields.add(qr);
    list.setCellRenderer(
        new DefaultListCellRenderer() {
          public Component getListCellRendererComponent(
              JList<?> l, Object v, int i, boolean selected, boolean focus) {
            return super.getListCellRendererComponent(
                l, v instanceof Equipo e ? e.codigo() + " · " + e.nombre() : v, i, selected, focus);
          }
        });
    JPanel buttons = new JPanel();
    buttons.add(remove);
    buttons.add(confirm);
    JPanel root = new JPanel(new BorderLayout(10, 10));
    root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
    root.add(fields, BorderLayout.NORTH);
    root.add(new JScrollPane(list));
    root.add(buttons, BorderLayout.SOUTH);
    setContentPane(root);
    setSize(720, 460);
    setLocationRelativeTo(owner);
    remove.addActionListener(
        e -> {
          int i = list.getSelectedIndex();
          if (i >= 0) model.remove(i);
        });
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
  }

  public void append(Equipo e) {
    for (int i = 0; i < model.size(); i++)
      if (model.get(i).id() == e.id())
        throw new IllegalArgumentException("El equipo ya está agregado.");
    model.addElement(e);
    code.setText("");
  }

  public java.util.List<Integer> ids() {
    java.util.List<Integer> ids = new ArrayList<>();
    for (int i = 0; i < model.size(); i++) ids.add(model.get(i).id());
    return ids;
  }

  public void busy(boolean busy) {
    add.setEnabled(!busy);
    qr.setEnabled(!busy);
    confirm.setEnabled(!busy);
    remove.setEnabled(!busy);
  }
}
