package cl.manitacrochet.pedidos.kafka;

import cl.manitacrochet.pedidos.kafka.consumer.LanaCatalogoConsumer;
import cl.manitacrochet.pedidos.kafka.dto.LanaKafkaEvent;
import cl.manitacrochet.pedidos.kafka.service.CatalogoLanasService;
import cl.manitacrochet.pedidos.kafka.support.EventoInvalidoException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LanaCatalogoConsumerTest {

    private final CatalogoLanasService catalogo = new CatalogoLanasService();
    private final LanaCatalogoConsumer consumer = new LanaCatalogoConsumer(catalogo);

    private LanaKafkaEvent evento(Long id, String nombre) {
        LanaKafkaEvent e = new LanaKafkaEvent();
        e.setLanaId(id);
        e.setNombre(nombre);
        return e;
    }

    @Test
    void eventoValidoActualizaElCatalogoLocal() {
        consumer.recibir(evento(7L, "Merino"), 0, 0L);

        assertEquals("Merino", catalogo.nombrePorId(7L).orElseThrow());
        assertEquals(1, catalogo.total());
    }

    @Test
    void eventoSinNombreEsInvalidoYNoTocaElCatalogo() {
        assertThrows(EventoInvalidoException.class, () -> consumer.recibir(evento(7L, " "), 0, 0L));
        assertEquals(0, catalogo.total());
    }

    @Test
    void eventoSinIdEsInvalido() {
        assertThrows(EventoInvalidoException.class, () -> consumer.recibir(evento(null, "Merino"), 0, 0L));
    }
}
