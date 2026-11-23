package cl.manitacrochet.kafkaadmin.dto;

/** Respuesta estándar de las operaciones de creación y modificación. */
public record AdminResponse(String recurso, String nombre, String mensaje) {
}
