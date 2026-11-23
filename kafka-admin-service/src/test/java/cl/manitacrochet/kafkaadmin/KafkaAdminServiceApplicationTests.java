package cl.manitacrochet.kafkaadmin;

import org.apache.kafka.clients.admin.Admin;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class KafkaAdminServiceApplicationTests {

    // Sin brokers en las pruebas: el AdminClient se simula.
    @MockBean
    private Admin admin;

    @Test
    void contextLoads() {
        // Levanta el contexto completo (seguridad + servicio de administración).
    }
}
