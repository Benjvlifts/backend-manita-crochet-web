package cl.manitacrochet.pedidos.kafka;

import cl.manitacrochet.pedidos.kafka.dto.PedidoKafkaEvent;
import cl.manitacrochet.pedidos.kafka.producer.PedidoKafkaProducer;
import cl.manitacrochet.pedidos.model.Pedido;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoKafkaProducerTest {

    @Mock
    private KafkaTemplate<String, PedidoKafkaEvent> kafkaTemplate;

    private PedidoKafkaProducer producer;

    @BeforeEach
    void setUp() {
        producer = new PedidoKafkaProducer(kafkaTemplate, "topico.test");
    }

    @Test
    void pedidoCreadoSePublicaConElTipoCorrectoYUnEventId() {
        when(kafkaTemplate.send(anyString(), anyString(), any(PedidoKafkaEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publicarPedidoCreado(new Pedido("Ana", 1L, 2));

        ArgumentCaptor<PedidoKafkaEvent> captor = ArgumentCaptor.forClass(PedidoKafkaEvent.class);
        verify(kafkaTemplate).send(eq("topico.test"), anyString(), captor.capture());
        assertEquals(PedidoKafkaEvent.Tipo.PEDIDO_CREADO, captor.getValue().getTipo());
        assertNotNull(captor.getValue().getEventId());
        assertEquals("Ana", captor.getValue().getCliente());
    }

    @Test
    void siKafkaFallaNoSePropagaLaExcepcion() {
        when(kafkaTemplate.send(anyString(), anyString(), any(PedidoKafkaEvent.class)))
                .thenThrow(new KafkaException("broker caído"));

        assertDoesNotThrow(() -> producer.publicarPedidoCreado(new Pedido("Ana", 1L, 2)));
    }
}
