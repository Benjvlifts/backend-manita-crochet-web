package cl.manitacrochet.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Entrada de POST /api/admin/bindings: conecta un exchange con una cola (o con otro exchange).
 * routingKey es opcional (los exchanges fanout no la usan).
 */
public record CrearBindingRequest(
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
