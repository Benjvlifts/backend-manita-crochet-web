package cl.manitacrochet.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Parámetros (query string) de DELETE /api/admin/bindings. Un binding se identifica por la
 * combinación exchange + destino + tipoDestino + routingKey, igual que al crearlo.
 */
public record EliminarBindingRequest(
        @NotBlank(message = "El exchange de origen es obligatorio")
        @Size(max = 255, message = "El exchange no puede superar 255 caracteres")
        String exchange,
        @NotBlank(message = "El destino (cola o exchange) es obligatorio")
        @Size(max = 255, message = "El destino no puede superar 255 caracteres")
        String destino,
        @NotBlank(message = "tipoDestino es obligatorio")
        @Pattern(regexp = "(?i)QUEUE|EXCHANGE", message = "tipoDestino debe ser QUEUE o EXCHANGE")
        String tipoDestino,
        @Size(max = 255, message = "routingKey no puede superar 255 caracteres")
        String routingKey) {
}
