package proyecto_sigelab;

import javax.swing.*;
import proyecto_sigelab.config.*;
import proyecto_sigelab.controller.LoginController;
import proyecto_sigelab.integration.FacialClient;
import proyecto_sigelab.repository.SqlRepository;
import proyecto_sigelab.service.AuthService;

public final class Launcher {
  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            AppConfig config = new AppConfig();
            SqlRepository db = new SqlRepository(new ConnectionFactory(config));
            new LoginController(db, new AuthService(db, new FacialClient(config)));
          } catch (Exception e) {
            JOptionPane.showMessageDialog(
                null, e.getMessage(), "SIGELAB", JOptionPane.ERROR_MESSAGE);
          }
        });
  }
}
