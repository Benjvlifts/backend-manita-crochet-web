package cl.manitacrochet.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class PedidosServiceApplicationTests {

    // Sin broker en las pruebas: el productor recibe un RabbitTemplate simulado.
    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void contextLoads() {
        // Verifica que el contexto de Spring (seguridad + beans de RabbitMQ) levanta sin errores.
    }
}
