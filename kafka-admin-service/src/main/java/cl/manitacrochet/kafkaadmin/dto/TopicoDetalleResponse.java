package cl.manitacrochet.kafkaadmin.dto;

import java.util.List;
import java.util.Map;

/** Detalle de un tópico: particiones (líder, réplicas, ISR) y las configuraciones explícitas del tópico. */
public record TopicoDetalleResponse(String nombre, int particiones, int factorReplicacion,
                                    List<ParticionResponse> detalle, Map<String, String> configuraciones) {

    public record ParticionResponse(int id, int lider, List<Integer> replicas, List<Integer> isr) {
    }
}
