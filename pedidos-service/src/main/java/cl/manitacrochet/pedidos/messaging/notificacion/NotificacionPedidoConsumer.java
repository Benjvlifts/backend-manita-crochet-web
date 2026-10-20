package cl.manitacrochet.pedidos.messaging.notificacion;

import cl.manitacrochet.pedidos.messaging.event.PedidoEvent;
import cl.manitacrochet.pedidos.messaging.support.AckHandler;
import cl.manitacrochet.pedidos.messaging.support.PedidoEventValidator;
import cl.manitacrochet.pedidos.messaging.support.RecoverableMessageException;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Dominio funcional: NOTIFICACIONES al cliente (paquete messaging.notificacion).
 * Consume pedidos.queue con @RabbitListener y delega la política de ACK/NACK/reintentos en AckHandler.
 */
@Component
public class NotificacionPedidoConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionPedidoConsumer.class);
    private static final String FLUJO = "notificacion";

    private final AckHandler ackHandler;
    private final String failurePrefix;

    public NotificacionPedidoConsumer(AckHandler ackHandler,
                                      @Value("${rabbitmq.demo.failure-prefix:}") String failurePrefix) {
        this.ackHandler = ackHandler;
        this.failurePrefix = failurePrefix;
    }

    @RabbitListener(queues = "${rabbitmq.queue.pedidos}")
    public void recibir(PedidoEvent evento, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("[{}] Mensaje recibido tag={} evento={}", FLUJO, deliveryTag, evento);
        ackHandler.procesar(FLUJO, evento, channel, deliveryTag, this::enviarNotificacion);
    }

    private void enviarNotificacion(PedidoEvent evento) {
        PedidoEventValidator.validar(evento);

        // Solo demostración: simula que el proveedor de notificaciones está caído (error recuperable).
        if (failurePrefix != null && !failurePrefix.isBlank() && evento.getCliente().startsWith(failurePrefix)) {
            throw new RecoverableMessageException("Proveedor de notificaciones no disponible (falla simulada)");
        }

        String texto = evento.getTipo() == PedidoEvent.Tipo.PEDIDO_CREADO
                ? "Recibimos tu pedido #" + evento.getPedidoId() + " (" + evento.getCantidad() + " unidades)"
                : "Tu pedido #" + evento.getPedidoId() + " ahora está " + evento.getEstado();
        log.info("[{}] Notificación enviada a '{}': {}", FLUJO, evento.getCliente(), texto);
    }
}
