package cl.manitacrochet.pedidos.kafka.producer;

import cl.manitacrochet.pedidos.kafka.dto.PedidoKafkaEvent;
import cl.manitacrochet.pedidos.model.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Productor Kafka: publica en pedidos.eventos cada pedido creado o con cambio de estado.
 * Es independiente del productor de RabbitMQ (PedidoProducer): cada uno falla por separado y,
 * si Kafka no está disponible, el pedido igual se guarda (solo queda el error en el log).
 */
@Component
public class PedidoKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(PedidoKafkaProducer.class);

    private final KafkaTemplate<String, PedidoKafkaEvent> kafkaTemplate;
    private final String topico;

    public PedidoKafkaProducer(KafkaTemplate<String, PedidoKafkaEvent> kafkaTemplate,
                               @Value("${kafka.topic.pedidos.name}") String topico) {
        this.kafkaTemplate = kafkaTemplate;
        this.topico = topico;
    }

    public void publicarPedidoCreado(Pedido pedido) {
        enviar(PedidoKafkaEvent.de(PedidoKafkaEvent.Tipo.PEDIDO_CREADO, pedido));
    }

    public void publicarEstadoActualizado(Pedido pedido) {
        enviar(PedidoKafkaEvent.de(PedidoKafkaEvent.Tipo.PEDIDO_ESTADO_ACTUALIZADO, pedido));
    }

    private void enviar(PedidoKafkaEvent evento) {
        try {
            // Key = pedidoId: todos los eventos de un pedido caen en la misma partición y se leen en orden.
            kafkaTemplate.send(topico, String.valueOf(evento.getPedidoId()), evento)
                    .whenComplete((resultado, error) -> {
                        if (error != null) {
                            log.error("[kafka] No se pudo publicar topic={} evento={} causa={}", topico, evento, error.getMessage());
                        } else if (resultado != null) {
                            log.info("[kafka] Evento publicado topic={} partition={} offset={} evento={}", topico,
                                    resultado.getRecordMetadata().partition(), resultado.getRecordMetadata().offset(), evento);
                        }
                    });
        } catch (RuntimeException e) {
            log.error("[kafka] No se pudo publicar (el pedido igual fue guardado) topic={} evento={} causa={}", topico, evento, e.getMessage());
        }
    }
}
