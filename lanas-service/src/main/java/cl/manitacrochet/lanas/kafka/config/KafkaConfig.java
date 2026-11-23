package cl.manitacrochet.lanas.kafka.config;

import cl.manitacrochet.lanas.kafka.support.EventoInvalidoException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Configuración de Kafka de lanas-service (el cliente en sí —brokers, serializers, consumer groups—
 * se define en application.properties bajo spring.kafka.* y kafka.*).
 *
 * Aquí solo hay dos cosas:
 *  1) El tópico del que este servicio es dueño (lanas.eventos), con sus parámetros de administración.
 *  2) La política de errores de TODOS los consumidores del servicio.
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    /**
     * lanas.eventos: estado del catálogo.
     *  - cleanup.policy=compact: Kafka conserva solo el último evento de cada lana (key = lanaId), no un historial.
     *  - factor de replicación 2: tolera la caída de un broker.
     */
    @Bean
    NewTopic lanasEventosTopic(
            @Value("${kafka.topic.lanas.name}") String nombre,
            @Value("${kafka.topic.lanas.partitions}") int particiones,
            @Value("${kafka.topic.lanas.replicas}") int replicas,
            @Value("${kafka.topic.lanas.min-insync-replicas}") int minIsr,
            @Value("${kafka.topic.lanas.cleanup-policy}") String cleanupPolicy,
            @Value("${kafka.topic.lanas.delete-retention-ms}") long deleteRetentionMs) {
        return TopicBuilder.name(nombre)
                .partitions(particiones)
                .replicas(replicas)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, cleanupPolicy)
                .config(TopicConfig.DELETE_RETENTION_MS_CONFIG, String.valueOf(deleteRetentionMs))
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(minIsr))
                .build();
    }

    /**
     * Manejo de errores al consumir (Spring Boot lo aplica a todos los @KafkaListener):
     *  - Error temporal: se reintenta con espera fija (kafka.consumer.retry.*).
     *  - Evento inválido o JSON corrupto: no se reintenta (no tiene arreglo).
     *  - Agotados los reintentos: queda registrado en el log con topic/partición/offset y se avanza al siguiente
     *    mensaje, así un mensaje defectuoso nunca detiene el procesamiento.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(
            @Value("${kafka.consumer.retry.attempts:3}") int intentos,
            @Value("${kafka.consumer.retry.backoff-ms:1000}") long esperaMs) {
        ConsumerRecordRecoverer descartar = (registro, error) -> log.error(
                "[kafka] Mensaje descartado topic={} partition={} offset={} key={} valor={} causa={}",
                registro.topic(), registro.partition(), registro.offset(), registro.key(), registro.value(),
                error.getMessage());
        DefaultErrorHandler handler = new DefaultErrorHandler(descartar, new FixedBackOff(esperaMs, Math.max(0, intentos - 1)));
        handler.addNotRetryableExceptions(EventoInvalidoException.class);
        return handler;
    }
}
