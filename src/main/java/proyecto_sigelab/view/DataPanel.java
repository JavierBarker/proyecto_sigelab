package proyecto_sigelab.view;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import proyecto_sigelab.model.Tabla;

public final class DataPanel extends JPanel {
  public final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
  private final JTable table = new JTable();

  public DataPanel() {
    super(new BorderLayout(8, 8));
    setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    add(actions, BorderLayout.NORTH);
    add(new JScrollPane(table));
    table.setRowHeight(28);
    table.setAutoCreateRowSorter(true);
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
  }

  public JButton button(String label, Runnable action) {
    JButton b = new JButton(label);
    b.addActionListener(e -> action.run());
    actions.add(b);
    return b;
  }

  public void display(Tabla data) {
    DefaultTableModel model =
        new DefaultTableModel(data.columnas().toArray(), 0) {
          @Override
          public boolean isCellEditable(int r, int c) {
            return false;
          }
        };
    for (var row : data.filas()) model.addRow(row.toArray());
    table.setModel(model);
  }

  public java.util.List<Object> selected() {
    int row = table.getSelectedRow();
    if (row < 0) throw new IllegalArgumentException("Seleccione una fila.");
    row = table.convertRowIndexToModel(row);
    java.util.List<Object> result = new ArrayList<>();
    for (int i = 0; i < table.getColumnCount(); i++)
      result.add(table.getModel().getValueAt(row, i));
    return result;
  }

  public java.util.List<Integer> selectedIds() {
    java.util.List<Integer> ids = new ArrayList<>();
    for (int row : table.getSelectedRows())
      ids.add(
          ((Number) table.getModel().getValueAt(table.convertRowIndexToModel(row), 0)).intValue());
    return ids;
  }

  public int selectedId() {
    return ((Number) selected().get(0)).intValue();
  }
}
