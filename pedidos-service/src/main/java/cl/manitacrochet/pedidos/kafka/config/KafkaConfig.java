package cl.manitacrochet.pedidos.kafka.config;

import cl.manitacrochet.pedidos.kafka.support.EventoInvalidoException;
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
 * Configuración de Kafka de pedidos-service (el cliente en sí —brokers, serializers, consumer groups—
 * se define en application.properties bajo spring.kafka.* y kafka.*).
 *
 * Aquí solo hay dos cosas:
 *  1) El tópico del que este servicio es dueño (pedidos.eventos), con sus parámetros de administración.
 *     KafkaAdmin lo crea al arrancar si no existe.
 *  2) La política de errores de TODOS los consumidores del servicio.
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    /**
     * pedidos.eventos: historial de pedidos.
     *  - 3 particiones: permiten hasta 3 consumidores del mismo grupo trabajando en paralelo.
     *  - factor de replicación 3 + min.insync.replicas 2: sobrevive a la caída de un broker sin perder datos.
     *  - retención de 7 días con cleanup.policy=delete: es un flujo de eventos, no un estado.
     */
    @Bean
    NewTopic pedidosEventosTopic(
            @Value("${kafka.topic.pedidos.name}") String nombre,
            @Value("${kafka.topic.pedidos.partitions}") int particiones,
            @Value("${kafka.topic.pedidos.replicas}") int replicas,
            @Value("${kafka.topic.pedidos.min-insync-replicas}") int minIsr,
            @Value("${kafka.topic.pedidos.retention-ms}") long retencionMs,
            @Value("${kafka.topic.pedidos.cleanup-policy}") String cleanupPolicy) {
        return TopicBuilder.name(nombre)
                .partitions(particiones)
                .replicas(replicas)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(retencionMs))
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, cleanupPolicy)
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
