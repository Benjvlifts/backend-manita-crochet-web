package cl.manitacrochet.pedidos.kafka.support;

/** Evento con datos inválidos: reintentar no sirve, el manejador de errores lo descarta sin reintentos. */
public class EventoInvalidoException extends RuntimeException {
    public EventoInvalidoException(String message) { super(message); }
}
