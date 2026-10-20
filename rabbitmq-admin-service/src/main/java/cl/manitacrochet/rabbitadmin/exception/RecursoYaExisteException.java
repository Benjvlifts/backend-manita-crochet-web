package cl.manitacrochet.rabbitadmin.exception;

/** El recurso que se intenta crear ya existe en el broker -> HTTP 409. */
public class RecursoYaExisteException extends RuntimeException {
    public RecursoYaExisteException(String message) { super(message); }
}
