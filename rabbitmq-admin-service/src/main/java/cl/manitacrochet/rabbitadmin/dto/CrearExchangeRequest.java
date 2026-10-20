package cl.manitacrochet.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Entrada de POST /api/admin/exchanges.
 * tipo: direct | topic | fanout | headers (sin distinguir mayúsculas). durable por defecto true.
 */
public record CrearExchangeRequest(
        @NotBlank(message = "El nombre del exchange es obligatorio")
        @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
        @Pattern(regexp = "^(?!amq\\.)[A-Za-z0-9._-]+$",
                message = "Nombre inválido: use solo letras, números, '.', '_' o '-', sin el prefijo reservado 'amq.'")
        String nombre,
        @NotBlank(message = "El tipo de exchange es obligatorio")
        @Pattern(regexp = "(?i)direct|topic|fanout|headers",
                message = "Tipo inválido: debe ser direct, topic, fanout o headers")
        String tipo,
        Boolean durable,
        Boolean autoDelete) {
}
