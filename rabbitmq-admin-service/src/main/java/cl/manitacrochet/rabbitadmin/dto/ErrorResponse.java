package cl.manitacrochet.rabbitadmin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** Cuerpo JSON de todos los errores de la API. "errores" detalla campo -> mensaje en validaciones (400). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(int status, String error, String message, Map<String, String> errores, Instant timestamp) {

    public static ErrorResponse de(int status, String error, String message) {
        return new ErrorResponse(status, error, message, null, Instant.now());
    }

    public static ErrorResponse de(int status, String error, String message, Map<String, String> errores) {
        return new ErrorResponse(status, error, message, errores, Instant.now());
    }
}
