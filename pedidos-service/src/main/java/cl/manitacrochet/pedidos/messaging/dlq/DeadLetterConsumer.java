package cl.manitacrochet.pedidos.messaging.dlq;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Dominio funcional: MENSAJES NO ENTREGADOS (paquete messaging.dlq).
 * Registra en logs lo que llega a las DLQ (pauta: "enviándolos a DLQ y registrándolos en logs").
 *
 * Está APAGADO por defecto (rabbitmq.dlq.consumer.enabled=false) para que los mensajes muertos
 * se queden en la DLQ y se puedan ver/reprocesar desde la consola de RabbitMQ. Al encenderlo,
 * cada mensaje se registra en el log y se confirma (ACK), vaciando la DLQ.
 * De todas formas, AckHandler ya registra cada mensaje en el momento exacto en que lo manda a la DLQ.
 */
@Component
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);

    @RabbitListener(
            queues = {"${rabbitmq.queue.pedidos-dlq}", "${rabbitmq.queue.tickets-dlq}"},
            autoStartup = "${rabbitmq.dlq.consumer.enabled:false}")
    public void registrar(Message mensaje, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.error("[DLQ] Mensaje no entregado. cola={} cuerpo={} x-death={}",
                mensaje.getMessageProperties().getConsumerQueue(),
                new String(mensaje.getBody(), StandardCharsets.UTF_8),
                mensaje.getMessageProperties().getXDeathHeader());
        channel.basicAck(deliveryTag, false);
    }
}
