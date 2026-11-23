package cl.manitacrochet.kafkaadmin.dto;

import java.util.List;

/** Detalle de un consumer group: estado, miembros y, por partición, hasta dónde leyó y cuánto le falta (lag). */
public record GrupoDetalleResponse(String id, String estado, int miembros, List<OffsetResponse> offsets, long lagTotal) {

    public record OffsetResponse(String topico, int particion, long offsetConfirmado, long offsetFinal, long lag) {
    }
}
