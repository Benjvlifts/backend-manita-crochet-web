package cl.manitacrochet.pedidos.messaging.ticket;

import cl.manitacrochet.pedidos.messaging.event.PedidoEvent;
import cl.manitacrochet.pedidos.messaging.support.AckHandler;
import cl.manitacrochet.pedidos.messaging.support.NonRecoverableMessageException;
import cl.manitacrochet.pedidos.messaging.support.PedidoEventValidator;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Dominio funcional: GENERACIÓN DE TICKETS (paquete messaging.ticket).
 * Consume pedidos.ticket.queue; solo procesa pedidos recién creados.
 */
@Component
public class TicketPedidoConsumer {

    private static final Logger log = LoggerFactory.getLogger(TicketPedidoConsumer.class);
    private static final String FLUJO = "ticket";

    private final AckHandler ackHandler;

    public TicketPedidoConsumer(AckHandler ackHandler) {
        this.ackHandler = ackHandler;
    }

    @RabbitListener(queues = "${rabbitmq.queue.tickets}")
    public void recibir(PedidoEvent evento, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("[{}] Mensaje recibido tag={} evento={}", FLUJO, deliveryTag, evento);
        ackHandler.procesar(FLUJO, evento, channel, deliveryTag, this::generarTicket);
    }

    private void generarTicket(PedidoEvent evento) {
        PedidoEventValidator.validar(evento);
        if (evento.getTipo() != PedidoEvent.Tipo.PEDIDO_CREADO) {
            throw new NonRecoverableMessageException("La cola de tickets solo acepta PEDIDO_CREADO, llegó " + evento.getTipo());
        }
        String codigo = "TCK-" + evento.getPedidoId() + "-" + System.currentTimeMillis();
        log.info("[{}] Ticket {} generado para el pedido #{} del cliente '{}'",
                FLUJO, codigo, evento.getPedidoId(), evento.getCliente());
    }
}
