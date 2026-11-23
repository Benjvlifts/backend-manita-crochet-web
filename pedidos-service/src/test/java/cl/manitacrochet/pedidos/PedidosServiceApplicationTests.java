package cl.manitacrochet.pedidos;

import cl.manitacrochet.pedidos.kafka.dto.PedidoKafkaEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"})
class PedidosServiceApplicationTests {

    // Sin brokers en las pruebas: los productores reciben plantillas simuladas.
    @MockBean
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private KafkaTemplate<String, PedidoKafkaEvent> kafkaTemplate;

    @Test
    void contextLoads() {
        // Verifica que el contexto de Spring (seguridad + RabbitMQ + Kafka) levanta sin errores.
    }
}
