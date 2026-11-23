package cl.manitacrochet.lanas.kafka;

import cl.manitacrochet.lanas.kafka.consumer.PedidoVentasConsumer;
import cl.manitacrochet.lanas.kafka.dto.PedidoKafkaEvent;
import cl.manitacrochet.lanas.kafka.service.VentasAnaliticaService;
import cl.manitacrochet.lanas.kafka.support.EventoInvalidoException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PedidoVentasConsumerTest {

    private final VentasAnaliticaService analitica = new VentasAnaliticaService();
    private final PedidoVentasConsumer consumer = new PedidoVentasConsumer(analitica, "FALLA-");

    private PedidoKafkaEvent evento(String cliente, Integer cantidad) {
        PedidoKafkaEvent e = new PedidoKafkaEvent();
        e.setEventId("evt-" + cliente + "-" + cantidad);
        e.setTipo(PedidoKafkaEvent.Tipo.PEDIDO_CREADO);
        e.setPedidoId(1L);
        e.setCliente(cliente);
        e.setLanaId(1L);
        e.setCantidad(cantidad);
        return e;
    }

    @Test
    void eventoValidoSeProcesa() {
        consumer.recibir(evento("Ana", 3), 0, 0L);

        assertEquals(3L, analitica.snapshot().unidadesTotales());
    }

    @Test
    void cantidadInvalidaEsErrorNoRecuperable() {
        assertThrows(EventoInvalidoException.class, () -> consumer.recibir(evento("Ana", 0), 0, 0L));
        assertEquals(0L, analitica.snapshot().unidadesTotales());
    }

    @Test
    void clienteConPrefijoDeDemoSimulaUnaFallaTemporal() {
        assertThrows(IllegalStateException.class, () -> consumer.recibir(evento("FALLA-Ana", 2), 0, 0L));
    }
}
