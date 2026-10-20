package cl.manitacrochet.rabbitadmin.exception;

import cl.manitacrochet.rabbitadmin.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce toda falla a una respuesta JSON clara (pauta: "los mensajes de error son claros").
 * Validación fallida -> 400 con el detalle campo por campo.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Falla de validación (@Valid sobre el cuerpo JSON o sobre los query params). */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleValidacion(BindException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            String mensaje = fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "valor inválido";
            errores.merge(fe.getField(), mensaje, (a, b) -> a + "; " + b);
        }
        return ResponseEntity.badRequest().body(
                ErrorResponse.de(400, "validation_error", "La petición contiene datos inválidos", errores));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonInvalido(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(
                ErrorResponse.de(400, "bad_request", "El cuerpo de la petición no es un JSON válido o está vacío"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleArgumentoInvalido(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.de(400, "bad_request", ex.getMessage()));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleNoEncontrado(RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.de(404, "not_found", ex.getMessage()));
    }

    @ExceptionHandler(RecursoYaExisteException.class)
    public ResponseEntity<ErrorResponse> handleYaExiste(RecursoYaExisteException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.de(409, "conflict", ex.getMessage()));
    }

    @ExceptionHandler(AmqpConnectException.class)
    public ResponseEntity<ErrorResponse> handleBrokerCaido(AmqpConnectException ex) {
        log.error("No hay conexión con RabbitMQ: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ErrorResponse.de(503, "broker_unavailable", "No se pudo conectar con RabbitMQ"));
    }

    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ErrorResponse> handleErrorBroker(AmqpException ex) {
        log.error("RabbitMQ rechazó la operación: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                ErrorResponse.de(502, "broker_error", "RabbitMQ rechazó la operación: " + ex.getMessage()));
    }
}
