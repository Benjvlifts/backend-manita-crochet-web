package cl.manitacrochet.pedidos.messaging.support;

/**
 * Error PERMANENTE: el mensaje es inválido y reintentar no sirve (ej. datos faltantes).
 * El AckHandler lo rechaza (nack sin requeue) de inmediato, sin reintentos -> DLQ.
 */
public class NonRecoverableMessageException extends RuntimeException {
    public NonRecoverableMessageException(String message) { super(message); }
}
