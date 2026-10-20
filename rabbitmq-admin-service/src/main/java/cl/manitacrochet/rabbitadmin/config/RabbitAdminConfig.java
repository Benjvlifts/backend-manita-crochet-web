package cl.manitacrochet.rabbitadmin.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expone RabbitAdmin (API de Spring AMQP para declarar/eliminar colas, exchanges y bindings)
 * como bean. La conexión se abre de forma perezosa: el servicio levanta aunque RabbitMQ esté caído.
 */
@Configuration
public class RabbitAdminConfig {

    @Bean
    RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}
