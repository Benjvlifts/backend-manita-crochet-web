package cl.manitacrochet.kafkaadmin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Entrada de PATCH /api/admin/kafka/topics/{nombre}/partitions. Kafka solo permite AUMENTAR particiones. */
public record AumentarParticionesRequest(
        @NotNull(message = "El nuevo número de particiones es obligatorio")
        @Min(value = 2, message = "El nuevo número de particiones debe ser al menos 2")
        Integer particiones) {
}
