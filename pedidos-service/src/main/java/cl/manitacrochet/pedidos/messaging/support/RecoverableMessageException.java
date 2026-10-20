package cl.manitacrochet.pedidos.messaging.support;

/**
 * Error TEMPORAL al procesar un mensaje (ej. un servicio externo que no responde).
 * El AckHandler reintenta; si se agotan los intentos, el mensaje se rechaza (nack) y va a la DLQ.
 */
public class RecoverableMessageException extends RuntimeException {
    public RecoverableMessageException(String message) { super(message); }
    public RecoverableMessageException(String message, Throwable cause) { super(message, cause); }
}
