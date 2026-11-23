package cl.manitacrochet.kafkaadmin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Entrada de POST /api/admin/kafka/topics.
 * configuraciones (opcional): parámetros del tópico, por ejemplo retention.ms, cleanup.policy, min.insync.replicas.
 */
public record CrearTopicoRequest(
        @NotBlank(message = "El nombre del tópico es obligatorio")
        @Size(max = 249, message = "El nombre no puede superar 249 caracteres")
        @Pattern(regexp = "^(?!\\.{1,2}$)[A-Za-z0-9._-]+$",
                message = "Nombre inválido: use solo letras, números, '.', '_' o '-' (y no '.' ni '..')")
        String nombre,
        @NotNull(message = "El número de particiones es obligatorio")
        @Min(value = 1, message = "Debe haber al menos 1 partición")
        @Max(value = 100, message = "Máximo 100 particiones")
        Integer particiones,
        @NotNull(message = "El factor de replicación es obligatorio")
        @Min(value = 1, message = "El factor de replicación debe ser al menos 1")
        @Max(value = 10, message = "Máximo factor de replicación: 10")
        Integer replicas,
        Map<String, String> configuraciones) {
}
