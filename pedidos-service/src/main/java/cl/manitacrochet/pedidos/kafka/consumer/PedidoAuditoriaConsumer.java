package cl.manitacrochet.pedidos.kafka.consumer;

import cl.manitacrochet.pedidos.kafka.dto.PedidoKafkaEvent;
import cl.manitacrochet.pedidos.kafka.service.AuditoriaPedidosService;
import cl.manitacrochet.pedidos.kafka.support.EventoInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumidor de pedidos.eventos para auditoría. Usa el grupo "pedidos-auditoria", distinto del grupo de
 * analítica de lanas-service: los dos grupos reciben TODOS los eventos (cada uno con su propio offset),
 * y dentro de este grupo las 3 particiones se reparten entre 3 hilos.
 */
@Component
public class PedidoAuditoriaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PedidoAuditoriaConsumer.class);

    private final AuditoriaPedidosService auditoria;

    public PedidoAuditoriaConsumer(AuditoriaPedidosService auditoria) {
        this.auditoria = auditoria;
    }

    @KafkaListener(
            topics = "${kafka.topic.pedidos.name}",
            groupId = "${kafka.consumer.group.auditoria}",
            concurrency = "${kafka.consumer.concurrency.auditoria:3}",
            properties = "spring.json.value.default.type=cl.manitacrochet.pedidos.kafka.dto.PedidoKafkaEvent")
    public void recibir(@Payload PedidoKafkaEvent evento,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
                        @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("[auditoria] Evento recibido partition={} offset={}", particion, offset);
        validar(evento);
        auditoria.registrar(evento);
    }

    void validar(PedidoKafkaEvent evento) {
        if (evento == null || evento.getPedidoId() == null || evento.getTipo() == null) {
            throw new EventoInvalidoException("Evento de pedido incompleto (falta pedidoId o tipo)");
        }
    }
}
