package cl.manitacrochet.pedidos.messaging.support;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Política común de confirmación (ACK) para TODOS los consumidores (modo de ack MANUAL).
 *
 * Pauta "manejo de confirmación y errores": cada mensaje termina siempre en una de estas rutas:
 *   1) Éxito                      -> basicAck               (el mensaje sale de la cola)
 *   2) Error NO recuperable       -> basicNack requeue=false (sin reintentos) -> DLX -> DLQ
 *   3) Error recuperable          -> se reintenta hasta rabbitmq.retry.max-attempts con backoff;
 *                                    si se agotan -> basicNack requeue=false -> DLX -> DLQ
 *   4) Hilo interrumpido (apagado) -> basicNack requeue=true (otro consumidor lo retoma)
 * Nunca se hace requeue en bucle, así no hay mensajes "venenosos" reprocesándose infinitamente.
 */
@Component
public class AckHandler {

    private static final Logger log = LoggerFactory.getLogger(AckHandler.class);

    /** Lógica de negocio a ejecutar sobre el mensaje ya deserializado. */
    @FunctionalInterface
    public interface MessageAction<T> {
        void run(T payload) throws Exception;
    }

    private final int maxAttempts;
    private final long backoffMs;

    public AckHandler(@Value("${rabbitmq.retry.max-attempts:3}") int maxAttempts,
                      @Value("${rabbitmq.retry.backoff-ms:1000}") long backoffMs) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMs = Math.max(0, backoffMs);
    }

    public <T> void procesar(String flujo, T payload, Channel channel, long deliveryTag, MessageAction<T> action) {
        int intento = 1;
        while (true) {
            try {
                action.run(payload);
                ack(flujo, channel, deliveryTag);
                log.info("[{}] Mensaje procesado correctamente (ACK) tag={} intento={}", flujo, deliveryTag, intento);
                return;
            } catch (NonRecoverableMessageException | IllegalArgumentException e) {
                log.error("[{}] Error NO recuperable, sin reintentos -> DLQ. tag={} motivo={} payload={}",
                        flujo, deliveryTag, e.getMessage(), payload);
                nack(flujo, channel, deliveryTag, false);
                return;
            } catch (Exception e) {
                if (intento >= maxAttempts) {
                    log.error("[{}] Reintentos agotados ({}/{}) -> DLQ. tag={} motivo={} payload={}",
                            flujo, intento, maxAttempts, deliveryTag, e.getMessage(), payload);
                    nack(flujo, channel, deliveryTag, false);
                    return;
                }
                log.warn("[{}] Error recuperable (intento {}/{}), se reintenta en {} ms. tag={} motivo={}",
                        flujo, intento, maxAttempts, backoffMs * intento, deliveryTag, e.getMessage());
                if (!esperar(backoffMs * intento)) {
                    log.warn("[{}] Hilo interrumpido; se devuelve el mensaje a la cola (requeue). tag={}", flujo, deliveryTag);
                    nack(flujo, channel, deliveryTag, true);
                    return;
                }
                intento++;
            }
        }
    }

    private void ack(String flujo, Channel channel, long tag) {
        try {
            channel.basicAck(tag, false);
        } catch (IOException e) {
            log.error("[{}] No se pudo enviar el ACK tag={}: {}", flujo, tag, e.getMessage());
        }
    }

    private void nack(String flujo, Channel channel, long tag, boolean requeue) {
        try {
            channel.basicNack(tag, false, requeue);
        } catch (IOException e) {
            log.error("[{}] No se pudo enviar el NACK tag={}: {}", flujo, tag, e.getMessage());
        }
    }

    private boolean esperar(long ms) {
        if (ms <= 0) {
            return true;
        }
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
