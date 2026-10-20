package cl.manitacrochet.rabbitadmin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RabbitAdminServiceApplicationTests {

    @Test
    void contextLoads() {
        // Levanta el contexto completo (seguridad + RabbitAdmin) sin necesidad de un broker activo:
        // la conexión a RabbitMQ es perezosa.
    }
}
