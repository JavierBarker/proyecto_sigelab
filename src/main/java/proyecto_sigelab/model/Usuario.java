package proyecto_sigelab.model;

public record Usuario(
    int id,
    String nombre,
    String apellido,
    String username,
    String hash,
    String correo,
    String rol,
    String estado) {
  public boolean activo() {
    return "ACTIVO".equals(estado);
  }

  public String nombreCompleto() {
    return nombre + " " + apellido;
  }
}
