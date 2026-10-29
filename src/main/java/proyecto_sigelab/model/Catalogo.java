package proyecto_sigelab.model;

/** Nombres SQL constantes: nunca se construyen identificadores desde texto del usuario. */
public enum Catalogo {
  LABORATORIO("Laboratorio", new String[] {"Codigo", "Nombre", "Ubicacion", "Capacidad", "Estado"}),
  TIPO("TipoEquipo", new String[] {"Nombre", "Estado"}),
  EQUIPO(
      "Equipo",
      new String[] {
        "CodigoInventario",
        "Nombre",
        "TipoEquipoId",
        "Marca",
        "Modelo",
        "NumeroSerie",
        "LaboratorioId",
        "Observaciones"
      });
  public final String tabla;
  public final String[] columnas;

  Catalogo(String tabla, String[] columnas) {
    this.tabla = tabla;
    this.columnas = columnas;
  }
}
