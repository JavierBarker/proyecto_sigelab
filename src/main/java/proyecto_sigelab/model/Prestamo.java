package proyecto_sigelab.model;

import java.time.LocalDateTime;

public record Prestamo(int id, String solicitante, LocalDateTime fechaEsperada, String estado) {}
