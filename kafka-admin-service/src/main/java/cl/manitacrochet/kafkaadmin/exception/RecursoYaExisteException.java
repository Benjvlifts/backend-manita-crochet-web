package cl.manitacrochet.kafkaadmin.exception;

/** El recurso que se intenta crear ya existe -> HTTP 409. */
public class RecursoYaExisteException extends RuntimeException {
    public RecursoYaExisteException(String message) { super(message); }
}
