package proyecto_sigelab.exception;

import java.sql.SQLException;

public class DataAccessException extends RuntimeException {
  public DataAccessException(SQLException e) {
    super(
        switch (e.getErrorCode()) {
          case 2601, 2627 ->
              "Ya existe un código, usuario o registro con esos datos. Actualice la pantalla e"
                  + " inténtelo de nuevo.";
          case 547 -> "La operación incumple una relación o restricción de la base de datos.";
          case 1205, 1222 ->
              "Otra operación está utilizando estos registros. Actualice e inténtelo de nuevo.";
          case 18456 -> "SQL Server rechazó el usuario o contraseña de conexión.";
          case 4060, 208 ->
              "No se encontró la base o sus tablas. Ejecute los scripts de instalación.";
          default ->
              "No se pudo completar la operación en SQL Server. Revise la conexión y los registros"
                  + " técnicos.";
        },
        e);
  }
}
