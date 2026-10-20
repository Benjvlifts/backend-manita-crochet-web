package cl.manitacrochet.pedidos.messaging;

import cl.manitacrochet.pedidos.messaging.event.PedidoEvent;
import cl.manitacrochet.pedidos.messaging.producer.PedidoProducer;
import cl.manitacrochet.pedidos.model.Pedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PedidoProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private PedidoProducer producer;

    @BeforeEach
    void setUp() {
        producer = new PedidoProducer(rabbitTemplate, "ex.test", "key.notificacion", "key.ticket");
    }

    @Test
    void pedidoCreadoPublicaNotificacionYTicket() {
        producer.publicarPedidoCreado(new Pedido("Ana", 1L, 2));

        verify(rabbitTemplate).convertAndSend(eq("ex.test"), eq("key.notificacion"), any(PedidoEvent.class));
        verify(rabbitTemplate).convertAndSend(eq("ex.test"), eq("key.ticket"), any(PedidoEvent.class));
    }

    @Test
    void estadoActualizadoPublicaSoloNotificacion() {
        producer.publicarEstadoActualizado(new Pedido("Ana", 1L, 2));

        verify(rabbitTemplate).convertAndSend(eq("ex.test"), eq("key.notificacion"), any(PedidoEvent.class));
    }

    @Test
    void siRabbitFallaNoSePropagaLaExcepcion() {
        doThrow(new AmqpConnectException(new RuntimeException("broker caído")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(PedidoEvent.class));

        assertDoesNotThrow(() -> producer.publicarPedidoCreado(new Pedido("Ana", 1L, 2)));
    }
}
