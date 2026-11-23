package cl.manitacrochet.lanas.kafka;

import cl.manitacrochet.lanas.kafka.dto.PedidoKafkaEvent;
import cl.manitacrochet.lanas.kafka.service.VentasAnaliticaService;
import cl.manitacrochet.lanas.kafka.service.VentasAnaliticaService.VentasSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VentasAnaliticaServiceTest {

    private final VentasAnaliticaService servicio = new VentasAnaliticaService();

    private PedidoKafkaEvent evento(String id, PedidoKafkaEvent.Tipo tipo, Long lanaId, int cantidad) {
        PedidoKafkaEvent e = new PedidoKafkaEvent();
        e.setEventId(id);
        e.setTipo(tipo);
        e.setPedidoId(1L);
        e.setCliente("Ana");
        e.setLanaId(lanaId);
        e.setCantidad(cantidad);
        return e;
    }

    @Test
    void sumaLasUnidadesPorLana() {
        servicio.registrar(evento("a", PedidoKafkaEvent.Tipo.PEDIDO_CREADO, 1L, 2));
        servicio.registrar(evento("b", PedidoKafkaEvent.Tipo.PEDIDO_CREADO, 1L, 3));
        servicio.registrar(evento("c", PedidoKafkaEvent.Tipo.PEDIDO_CREADO, 2L, 4));

        VentasSnapshot r = servicio.snapshot();
        assertEquals(5L, r.unidadesPorLana().get(1L));
        assertEquals(4L, r.unidadesPorLana().get(2L));
        assertEquals(3L, r.pedidosProcesados());
        assertEquals(9L, r.unidadesTotales());
    }

    @Test
    void unEventoDuplicadoNoSeCuentaDosVeces() {
        servicio.registrar(evento("a", PedidoKafkaEvent.Tipo.PEDIDO_CREADO, 1L, 2));
        servicio.registrar(evento("a", PedidoKafkaEvent.Tipo.PEDIDO_CREADO, 1L, 2));

        assertEquals(2L, servicio.snapshot().unidadesTotales());
    }

    @Test
    void unCambioDeEstadoNoSumaVentas() {
        servicio.registrar(evento("a", PedidoKafkaEvent.Tipo.PEDIDO_ESTADO_ACTUALIZADO, 1L, 2));

        assertEquals(0L, servicio.snapshot().unidadesTotales());
    }
}
