package cl.manitacrochet.pedidos.messaging.producer;

import cl.manitacrochet.pedidos.messaging.event.PedidoEvent;
import cl.manitacrochet.pedidos.model.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Productor de eventos de pedidos (pauta: "los microservicios deben incluir productores").
 * Encapsula el uso de RabbitTemplate: el controlador solo llama a métodos de negocio.
 *
 * Si RabbitMQ no está disponible NO se rompe la creación/actualización del pedido: se registra el
 * error y el flujo principal sigue ("mensajería desacoplada, sin afectar la lógica existente").
 */
@Component
public class PedidoProducer {

    private static final Logger log = LoggerFactory.getLogger(PedidoProducer.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String notificacionKey;
    private final String ticketKey;

    public PedidoProducer(RabbitTemplate rabbitTemplate,
                          @Value("${rabbitmq.exchange.pedidos}") String exchange,
                          @Value("${rabbitmq.routing-key.pedidos}") String notificacionKey,
                          @Value("${rabbitmq.routing-key.tickets}") String ticketKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.notificacionKey = notificacionKey;
        this.ticketKey = ticketKey;
    }

    /** Pedido nuevo: dispara una notificación y la generación del ticket. */
    public void publicarPedidoCreado(Pedido pedido) {
        PedidoEvent evento = PedidoEvent.de(PedidoEvent.Tipo.PEDIDO_CREADO, pedido);
        enviar(notificacionKey, evento);
        enviar(ticketKey, evento);
    }

    /** Cambio de estado: solo dispara una notificación al cliente. */
    public void publicarEstadoActualizado(Pedido pedido) {
        enviar(notificacionKey, PedidoEvent.de(PedidoEvent.Tipo.PEDIDO_ESTADO_ACTUALIZADO, pedido));
    }

    private void enviar(String routingKey, PedidoEvent evento) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, evento);
            log.info("Evento publicado exchange={} routingKey={} evento={}", exchange, routingKey, evento);
        } catch (AmqpException e) {
            log.error("No se pudo publicar el evento (el pedido igual fue guardado) exchange={} routingKey={} evento={} causa={}",
                    exchange, routingKey, evento, e.getMessage());
        }
    }
}
