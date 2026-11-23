package cl.manitacrochet.kafkaadmin.exception;

/** El recurso (tópico, grupo) no existe en Kafka -> HTTP 404. */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String message) { super(message); }
}
