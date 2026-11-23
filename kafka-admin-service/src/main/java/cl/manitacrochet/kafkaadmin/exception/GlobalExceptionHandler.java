package cl.manitacrochet.kafkaadmin.exception;

import cl.manitacrochet.kafkaadmin.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Traduce toda falla a una respuesta JSON clara. Validación fallida -> 400 con el detalle campo por campo.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(BrokerNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> handleBrokerCaido(BrokerNoDisponibleException ex) {
        log.error("Kafka no respondió: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ErrorResponse.de(503, "broker_unavailable", "No se pudo conectar con Kafka: " + ex.getMessage()));
    }

    @ExceptionHandler(KafkaOperacionException.class)
    public ResponseEntity<ErrorResponse> handleErrorKafka(KafkaOperacionException ex) {
        log.error("Kafka rechazó la operación: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponse.de(502, "broker_error", ex.getMessage()));
    }
}
