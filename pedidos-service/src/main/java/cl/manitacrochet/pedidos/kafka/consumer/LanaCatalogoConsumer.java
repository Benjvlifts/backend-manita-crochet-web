package cl.manitacrochet.pedidos.kafka.consumer;

import cl.manitacrochet.pedidos.kafka.dto.LanaKafkaEvent;
import cl.manitacrochet.pedidos.kafka.service.CatalogoLanasService;
import cl.manitacrochet.pedidos.kafka.support.EventoInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumidor de lanas.eventos. Capa de mensajería: recibe, valida y delega; la lógica de negocio
 * vive en CatalogoLanasService.
 *
 * Consumer group "pedidos-catalogo": si se levantan varias instancias de pedidos-service, Kafka reparte
 * las particiones entre ellas; "concurrency" hace lo mismo entre hilos de una misma instancia.
 */
@Component
public class LanaCatalogoConsumer {

    private static final Logger log = LoggerFactory.getLogger(LanaCatalogoConsumer.class);

    private final CatalogoLanasService catalogo;

    public LanaCatalogoConsumer(CatalogoLanasService catalogo) {
        this.catalogo = catalogo;
    }

    @KafkaListener(
            topics = "${kafka.topic.lanas.name}",
            groupId = "${kafka.consumer.group.catalogo}",
            concurrency = "${kafka.consumer.concurrency.catalogo:2}",
            properties = "spring.json.value.default.type=cl.manitacrochet.pedidos.kafka.dto.LanaKafkaEvent")
    public void recibir(@Payload LanaKafkaEvent evento,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
                        @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("[catalogo] Evento recibido partition={} offset={} evento={}", particion, offset, evento);
        validar(evento);
        catalogo.registrar(evento);
    }

    void validar(LanaKafkaEvent evento) {
        if (evento == null || evento.getLanaId() == null) {
            throw new EventoInvalidoException("Evento de lana sin lanaId");
        }
        if (evento.getNombre() == null || evento.getNombre().isBlank()) {
            throw new EventoInvalidoException("Evento de lana sin nombre");
        }
    }
}
