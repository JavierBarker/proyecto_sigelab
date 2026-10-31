package proyecto_sigelab.view;

import java.awt.*;
import java.util.*;
import javax.swing.*;

public final class Forms {
  public record Option(int id, String label) {
    @Override
    public String toString() {
      return label;
    }
  }

  private final LinkedHashMap<String, JComponent> fields = new LinkedHashMap<>();

  public Forms text(String key, String value) {
    fields.put(key, new JTextField(value == null ? "" : value, 25));
    return this;
  }

  public Forms password(String key) {
    fields.put(key, new JPasswordField(25));
    return this;
  }

  public Forms choices(String key, String... values) {
    fields.put(key, new JComboBox<>(values));
    return this;
  }

  public Forms options(String key, java.util.List<Option> values) {
    fields.put(key, new JComboBox<>(values.toArray(Option[]::new)));
    return this;
  }

  public void select(String key, Object value) {
    var component = fields.get(key);
    if (component instanceof JComboBox<?> box) {
      for (int i = 0; i < box.getItemCount(); i++) {
        Object item = box.getItemAt(i);
        if (item instanceof Option o && String.valueOf(o.id()).equals(String.valueOf(value))
            || String.valueOf(item).equals(String.valueOf(value))) {
          box.setSelectedIndex(i);
          break;
        }
      }
    }
  }

  public String value(String key) {
    var c = fields.get(key);
    if (c instanceof JPasswordField p) return new String(p.getPassword());
    if (c instanceof JTextField t) return t.getText();
    Object v = ((JComboBox<?>) c).getSelectedItem();
    return v instanceof Option o ? String.valueOf(o.id()) : v == null ? "" : v.toString();
  }

  public boolean show(Component parent, String title) {
    JPanel p = new JPanel(new GridLayout(0, 2, 10, 8));
    fields.forEach(
        (key, v) -> {
          p.add(new JLabel(key));
          p.add(v);
        });
    return JOptionPane.showConfirmDialog(
            parent, p, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        == JOptionPane.OK_OPTION;
  }

  public static void info(Component parent, String text) {
    JOptionPane.showMessageDialog(parent, text, "SIGELAB", JOptionPane.INFORMATION_MESSAGE);
  }

  public static void error(Component parent, String text) {
    JOptionPane.showMessageDialog(
        parent, text, "No se completó la operación", JOptionPane.WARNING_MESSAGE);
  }
}
