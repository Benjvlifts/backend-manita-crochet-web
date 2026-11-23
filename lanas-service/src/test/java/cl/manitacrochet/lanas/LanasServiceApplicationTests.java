package cl.manitacrochet.lanas;

import cl.manitacrochet.lanas.kafka.dto.LanaKafkaEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"})
class LanasServiceApplicationTests {

    // Sin brokers en las pruebas: el productor recibe una plantilla simulada.
    @MockBean
    private KafkaTemplate<String, LanaKafkaEvent> kafkaTemplate;

    @Test
    void contextLoads() {
        // Verifica que todo el contexto de Spring (seguridad con JwtDecoder + Kafka) levanta sin errores.
    }
}
