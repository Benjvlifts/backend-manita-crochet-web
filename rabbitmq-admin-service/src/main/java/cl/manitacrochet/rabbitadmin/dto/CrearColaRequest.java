package cl.manitacrochet.rabbitadmin.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Entrada de POST /api/admin/queues. Validación (pauta: "no es posible crear colas con nombres
 * vacíos ni con configuraciones inválidas"):
 *  - nombre: obligatorio, hasta 255 caracteres, solo letras/números/punto/guion/guion bajo y
 *    sin el prefijo reservado "amq." (lo usa el propio RabbitMQ).
 *  - messageTtlMs: si se envía, debe ser positivo.
 *  - deadLetterRoutingKey solo tiene sentido junto con deadLetterExchange.
 * Opcionales: durable (por defecto true) y autoDelete (por defecto false).
 */
public record CrearColaRequest(
        @NotBlank(message = "El nombre de la cola es obligatorio")
        @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
        @Pattern(regexp = "^(?!amq\\.)[A-Za-z0-9._-]+$",
                message = "Nombre inválido: use solo letras, números, '.', '_' o '-', sin el prefijo reservado 'amq.'")
        String nombre,
        Boolean durable,
        Boolean autoDelete,
        @Size(max = 255, message = "deadLetterExchange no puede superar 255 caracteres")
        String deadLetterExchange,
        @Size(max = 255, message = "deadLetterRoutingKey no puede superar 255 caracteres")
        String deadLetterRoutingKey,
        @Positive(message = "messageTtlMs debe ser mayor que 0")
        Long messageTtlMs) {

    @AssertTrue(message = "deadLetterRoutingKey requiere indicar también deadLetterExchange")
    public boolean isDeadLetterValido() {
        boolean hayKey = deadLetterRoutingKey != null && !deadLetterRoutingKey.isBlank();
        boolean hayExchange = deadLetterExchange != null && !deadLetterExchange.isBlank();
        return !hayKey || hayExchange;
    }
}
