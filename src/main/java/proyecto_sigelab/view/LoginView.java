package proyecto_sigelab.view;

import java.awt.*;
import javax.swing.*;

public final class LoginView extends JFrame {
  public final JTextField username = new JTextField(22);
  public final JPasswordField password = new JPasswordField(22);
  public final JButton login = new JButton("Ingresar con contraseña y rostro"),
      enroll = new JButton("Registrar mi rostro por primera vez"),
      bootstrap = new JButton("Crear administrador inicial");

  public LoginView() {
    super("SIGELAB · Acceso");
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    JPanel p = new JPanel(new GridLayout(0, 1, 10, 10));
    p.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
    JLabel title = new JLabel("SIGELAB", SwingConstants.CENTER);
    title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
    p.add(title);
    p.add(new JLabel("Gestión de laboratorios universitarios"));
    p.add(new JLabel("Usuario"));
    p.add(username);
    p.add(new JLabel("Contraseña"));
    p.add(password);
    p.add(login);
    p.add(enroll);
    p.add(bootstrap);
    p.add(new JLabel("El ingreso requiere un rostro previamente registrado."));
    setContentPane(p);
    pack();
    setLocationRelativeTo(null);
    getRootPane().setDefaultButton(login);
  }
}
