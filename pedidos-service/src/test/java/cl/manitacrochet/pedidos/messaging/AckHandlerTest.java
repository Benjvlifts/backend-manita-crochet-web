package cl.manitacrochet.pedidos.messaging;

import cl.manitacrochet.pedidos.messaging.support.AckHandler;
import cl.manitacrochet.pedidos.messaging.support.NonRecoverableMessageException;
import cl.manitacrochet.pedidos.messaging.support.RecoverableMessageException;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Verifica las tres rutas de un mensaje: ACK, NACK directo a DLQ y reintentos + NACK. */
@ExtendWith(MockitoExtension.class)
class AckHandlerTest {

    private static final long TAG = 7L;

    @Mock
    private Channel channel;

    // 3 intentos máximo y sin espera entre ellos para que la prueba sea instantánea.
    private final AckHandler handler = new AckHandler(3, 0);

    @Test
    void procesamientoExitosoHaceAck() throws Exception {
        handler.procesar("test", "msg", channel, TAG, m -> { });

        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void errorNoRecuperableVaAlDlqSinReintentos() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();

        handler.procesar("test", "msg", channel, TAG, m -> {
            llamadas.incrementAndGet();
            throw new NonRecoverableMessageException("mensaje inválido");
        });

        assertEquals(1, llamadas.get());
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void errorRecuperableSeReintentaYAlAgotarseVaAlDlq() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();

        handler.procesar("test", "msg", channel, TAG, m -> {
            llamadas.incrementAndGet();
            throw new RecoverableMessageException("servicio caído");
        });

        assertEquals(3, llamadas.get());
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void errorRecuperableQueSeResuelveHaceAck() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();

        handler.procesar("test", "msg", channel, TAG, m -> {
            if (llamadas.incrementAndGet() < 3) {
                throw new RecoverableMessageException("falla temporal");
            }
        });

        assertEquals(3, llamadas.get());
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }
}
