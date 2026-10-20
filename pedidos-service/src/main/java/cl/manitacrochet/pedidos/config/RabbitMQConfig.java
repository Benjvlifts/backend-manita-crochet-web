package cl.manitacrochet.pedidos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ÚNICA clase de configuración de mensajería de pedidos-service (EP3).
 *
 * Cumple la pauta así:
 *  - "Define ... de forma centralizada": ningún nombre está escrito en código; todos vienen de
 *    application.properties (rabbitmq.*).
 *  - "Declara beans de configuración con Queue, Exchange y Binding para cada caso de uso":
 *    hay un bean explícito por cada pieza, para dos casos de uso (ver mapa de rutas abajo).
 *  - "No mezcla la lógica de negocio con la configuración": controladores, productor y
 *    consumidores solo usan lo que se declara aquí; no crean colas ni exchanges.
 *
 * Mapa de rutas:
 *
 *   CASO 1 — Notificaciones (pedido creado / estado actualizado)
 *     pedidos.exchange (topic) --[pedidos.routing.key]--> pedidos.queue
 *     pedidos.queue --(rechazo)--> pedidos.dlx (direct) --[pedidos.dlq.routing.key]--> pedidos.dlq
 *
 *   CASO 2 — Generación de tickets (solo pedido creado)
 *     pedidos.exchange (topic) --[pedidos.ticket.routing.key]--> pedidos.ticket.queue
 *     pedidos.ticket.queue --(rechazo)--> pedidos.dlx (direct) --[pedidos.ticket.dlq.routing.key]--> pedidos.ticket.dlq
 *
 * Spring Boot crea un RabbitAdmin que declara estos beans en el broker al conectarse.
 */
@Configuration
public class RabbitMQConfig {

    private static final String ARG_DLX = "x-dead-letter-exchange";
    private static final String ARG_DLX_ROUTING_KEY = "x-dead-letter-routing-key";

    // --- Exchanges ---
    @Value("${rabbitmq.exchange.pedidos}")
    private String pedidosExchangeName;
    @Value("${rabbitmq.exchange.dlx}")
    private String dlxName;

    // --- Colas ---
    @Value("${rabbitmq.queue.pedidos}")
    private String pedidosQueueName;
    @Value("${rabbitmq.queue.pedidos-dlq}")
    private String pedidosDlqName;
    @Value("${rabbitmq.queue.tickets}")
    private String ticketsQueueName;
    @Value("${rabbitmq.queue.tickets-dlq}")
    private String ticketsDlqName;

    // --- Routing keys ---
    @Value("${rabbitmq.routing-key.pedidos}")
    private String pedidosKey;
    @Value("${rabbitmq.routing-key.pedidos-dlq}")
    private String pedidosDlqKey;
    @Value("${rabbitmq.routing-key.tickets}")
    private String ticketsKey;
    @Value("${rabbitmq.routing-key.tickets-dlq}")
    private String ticketsDlqKey;

    // ===================== Exchanges =====================

    /** Main Exchange: recibe los eventos de pedidos y los enruta por routing key. */
    @Bean
    TopicExchange pedidosExchange() {
        return new TopicExchange(pedidosExchangeName, true, false);
    }

    /** Dead Letter Exchange: recibe los mensajes rechazados de cualquier cola principal. */
    @Bean
    DirectExchange pedidosDlx() {
        return new DirectExchange(dlxName, true, false);
    }

    // ===================== Caso 1: notificaciones =====================

    /** Main Queue: al rechazar un mensaje (nack sin requeue) este se redirige al DLX. */
    @Bean
    Queue pedidosQueue() {
        return QueueBuilder.durable(pedidosQueueName)
                .withArgument(ARG_DLX, dlxName)
                .withArgument(ARG_DLX_ROUTING_KEY, pedidosDlqKey)
                .build();
    }

    @Bean
    Queue pedidosDlq() {
        return QueueBuilder.durable(pedidosDlqName).build();
    }

    @Bean
    Binding pedidosBinding(Queue pedidosQueue, TopicExchange pedidosExchange) {
        return BindingBuilder.bind(pedidosQueue).to(pedidosExchange).with(pedidosKey);
    }

    @Bean
    Binding pedidosDlqBinding(Queue pedidosDlq, DirectExchange pedidosDlx) {
        return BindingBuilder.bind(pedidosDlq).to(pedidosDlx).with(pedidosDlqKey);
    }

    // ===================== Caso 2: tickets =====================

    @Bean
    Queue ticketsQueue() {
        return QueueBuilder.durable(ticketsQueueName)
                .withArgument(ARG_DLX, dlxName)
                .withArgument(ARG_DLX_ROUTING_KEY, ticketsDlqKey)
                .build();
    }

    @Bean
    Queue ticketsDlq() {
        return QueueBuilder.durable(ticketsDlqName).build();
    }

    @Bean
    Binding ticketsBinding(Queue ticketsQueue, TopicExchange pedidosExchange) {
        return BindingBuilder.bind(ticketsQueue).to(pedidosExchange).with(ticketsKey);
    }

    @Bean
    Binding ticketsDlqBinding(Queue ticketsDlq, DirectExchange pedidosDlx) {
        return BindingBuilder.bind(ticketsDlq).to(pedidosDlx).with(ticketsDlqKey);
    }

    // ===================== Serialización =====================

    /**
     * Payloads como JSON (no serialización Java). Usa el ObjectMapper de Spring Boot, que ya soporta
     * java.time.Instant. Spring Boot lo aplica solo a RabbitTemplate y a los @RabbitListener.
     */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
