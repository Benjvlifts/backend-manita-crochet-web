package cl.manitacrochet.rabbitadmin.exception;

/** El recurso (cola, exchange) no existe en el broker -> HTTP 404. */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String message) { super(message); }
}
