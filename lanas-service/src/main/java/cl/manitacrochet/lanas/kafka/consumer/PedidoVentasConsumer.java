package cl.manitacrochet.lanas.kafka.consumer;

import cl.manitacrochet.lanas.kafka.dto.PedidoKafkaEvent;
import cl.manitacrochet.lanas.kafka.service.VentasAnaliticaService;
import cl.manitacrochet.lanas.kafka.support.EventoInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumidor de pedidos.eventos para la analítica de ventas. Capa de mensajería: recibe, valida y delega;
 * la lógica de negocio vive en VentasAnaliticaService.
 *
 * Consumer group "lanas-analitica": las 3 particiones del tópico se reparten entre los 3 hilos
 * (concurrency); con varias instancias de lanas-service, se reparten entre todas. Es un grupo distinto
 * del de pedidos-service ("pedidos-auditoria"), así ambos reciben todos los eventos.
 */
@Component
public class PedidoVentasConsumer {

    private static final Logger log = LoggerFactory.getLogger(PedidoVentasConsumer.class);

    private final VentasAnaliticaService analitica;
    private final String prefijoFallaDemo;

    public PedidoVentasConsumer(VentasAnaliticaService analitica,
                                @Value("${kafka.demo.failure-prefix:}") String prefijoFallaDemo) {
        this.analitica = analitica;
        this.prefijoFallaDemo = prefijoFallaDemo;
    }

    @KafkaListener(
            topics = "${kafka.topic.pedidos.name}",
            groupId = "${kafka.consumer.group.ventas}",
            concurrency = "${kafka.consumer.concurrency.ventas:3}",
            properties = "spring.json.value.default.type=cl.manitacrochet.lanas.kafka.dto.PedidoKafkaEvent")
    public void recibir(@Payload PedidoKafkaEvent evento,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
                        @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("[analitica] Evento recibido partition={} offset={} evento={}", particion, offset, evento);
        validar(evento);

        // Solo demostración: simula que la analítica está caída (error temporal -> reintentos -> descarte).
        if (prefijoFallaDemo != null && !prefijoFallaDemo.isBlank() && evento.getCliente().startsWith(prefijoFallaDemo)) {
            throw new IllegalStateException("Servicio de analítica no disponible (falla simulada)");
        }
        analitica.registrar(evento);
    }

    void validar(PedidoKafkaEvent evento) {
        if (evento == null || evento.getEventId() == null || evento.getTipo() == null || evento.getPedidoId() == null) {
            throw new EventoInvalidoException("Evento de pedido incompleto (falta eventId, tipo o pedidoId)");
        }
        if (evento.getCliente() == null || evento.getCliente().isBlank()) {
            throw new EventoInvalidoException("Evento de pedido sin cliente");
        }
        if (evento.getLanaId() == null) {
            throw new EventoInvalidoException("Evento de pedido sin lanaId");
        }
        if (evento.getCantidad() == null || evento.getCantidad() <= 0) {
            throw new EventoInvalidoException("Cantidad inválida: " + evento.getCantidad());
        }
    }
}
