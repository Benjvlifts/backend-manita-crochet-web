package cl.manitacrochet.lanas.kafka.producer;

import cl.manitacrochet.lanas.kafka.dto.LanaKafkaEvent;
import cl.manitacrochet.lanas.model.Lana;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Productor Kafka: publica en lanas.eventos cada lana creada. Si Kafka no está disponible,
 * la lana igual se guarda (solo queda el error en el log).
 */
@Component
public class LanaKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(LanaKafkaProducer.class);

    private final KafkaTemplate<String, LanaKafkaEvent> kafkaTemplate;
    private final String topico;

    public LanaKafkaProducer(KafkaTemplate<String, LanaKafkaEvent> kafkaTemplate,
                             @Value("${kafka.topic.lanas.name}") String topico) {
        this.kafkaTemplate = kafkaTemplate;
        this.topico = topico;
    }

    public void publicarLanaCreada(Lana lana) {
        LanaKafkaEvent evento = LanaKafkaEvent.de(LanaKafkaEvent.Tipo.LANA_CREADA, lana);
        try {
            // Key = lanaId: con cleanup.policy=compact Kafka conserva el último evento de cada lana.
            kafkaTemplate.send(topico, String.valueOf(evento.getLanaId()), evento)
                    .whenComplete((resultado, error) -> {
                        if (error != null) {
                            log.error("[kafka] No se pudo publicar topic={} evento={} causa={}", topico, evento, error.getMessage());
                        } else if (resultado != null) {
                            log.info("[kafka] Evento publicado topic={} partition={} offset={} evento={}", topico,
                                    resultado.getRecordMetadata().partition(), resultado.getRecordMetadata().offset(), evento);
                        }
                    });
        } catch (RuntimeException e) {
            log.error("[kafka] No se pudo publicar (la lana igual fue guardada) topic={} evento={} causa={}", topico, evento, e.getMessage());
        }
    }
}
