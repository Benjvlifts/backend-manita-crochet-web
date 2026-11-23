package cl.manitacrochet.kafkaadmin.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/** Entrada de PUT /api/admin/kafka/topics/{nombre}/config: parámetros a modificar (clave -> valor). */
public record ActualizarConfigRequest(
        @NotEmpty(message = "Debe indicar al menos una configuración a modificar")
        Map<String, String> configuraciones) {
}
