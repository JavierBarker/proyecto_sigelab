import javax.swing.*;
import java.util.Arrays;
import proyecto_sigelab.config.*;
import proyecto_sigelab.service.AuthService;

class RecuperarAcceso {
    public static void main(String[] args) throws Exception {
        JPasswordField clave = new JPasswordField();
        JPasswordField confirmar = new JPasswordField();
        Object[] campos = {
            "Nueva contrasena para ale.admin (minimo 8 caracteres):",
            clave, "Repita la contrasena:", confirmar
        };
        if (JOptionPane.showConfirmDialog(null, campos,
                "Restablecer acceso", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) return;

        if (!Arrays.equals(clave.getPassword(), confirmar.getPassword())) {
            System.out.println("Las contrasenas no coinciden. No se cambio nada.");
            return;
        }

        String hash = AuthService.hashPassword(new String(clave.getPassword()));
        try (var c = new ConnectionFactory(new AppConfig()).open()) {
            c.setAutoCommit(false);
            try (var p = c.prepareStatement(
                    "UPDATE dbo.Usuario SET PasswordHash=? " +
                    "WHERE NombreUsuario=? AND Estado='ACTIVO'")) {
                p.setString(1, hash);
                p.setString(2, "ale.admin");
                if (p.executeUpdate() != 1) {
                    c.rollback();
                    throw new IllegalStateException("No se encontro una cuenta unica.");
                }
                c.commit();
            }
        }
        System.out.println("Contrasena de ale.admin actualizada correctamente.");
    }
}
