package proyecto_sigelab.view;

import java.awt.*;
import javax.swing.*;
import proyecto_sigelab.model.Session;

public final class MainView extends JFrame {
  public final JTabbedPane tabs = new JTabbedPane();

  public MainView(Session session) {
    super("SIGELAB · " + session.nombre() + " · " + session.rol());
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    add(tabs);
    setSize(1200, 720);
    setMinimumSize(new Dimension(950, 550));
    setLocationRelativeTo(null);
  }

  public DataPanel tab(String name) {
    DataPanel p = new DataPanel();
    tabs.addTab(name, p);
    return p;
  }
}
