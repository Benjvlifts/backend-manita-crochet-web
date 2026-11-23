package cl.manitacrochet.kafkaadmin.config;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Cliente administrativo de Kafka (AdminClient). La conexión es perezosa: el servicio levanta
 * aunque los brokers estén caídos y responde 503 cuando se intenta administrar.
 */
@Configuration
public class KafkaAdminConfig {

    @Bean(destroyMethod = "close")
    Admin kafkaAdminClient(@Value("${kafka.bootstrap-servers}") String bootstrapServers,
                           @Value("${kafka.admin.request-timeout-ms:5000}") int requestTimeoutMs,
                           @Value("${kafka.admin.timeout-ms:8000}") int apiTimeoutMs) {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, apiTimeoutMs);
        return Admin.create(config);
    }
}
