package cl.manitacrochet.pedidos.config;

import cl.manitacrochet.pedidos.model.Pedido;
import cl.manitacrochet.pedidos.repository.PedidoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataLoader {

    @Bean
    CommandLineRunner seed(PedidoRepository repo) {
        return args -> {
            if (repo.count() == 0) {
                repo.save(new Pedido("Benjamin", 1L, 2));
                repo.save(new Pedido("María", 2L, 1));
            }
        };
    }
}
