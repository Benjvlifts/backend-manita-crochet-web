package cl.manitacrochet.rabbitadmin.dto;

/** Estado de una cola: útil para ver cuántos mensajes hay (por ejemplo en una DLQ). */
public record ColaInfoResponse(String nombre, int mensajes, int consumidores) {
}
