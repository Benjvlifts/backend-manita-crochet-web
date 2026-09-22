package cl.manitacrochet.lanas.config;

import cl.manitacrochet.lanas.model.Lana;
import cl.manitacrochet.lanas.repository.LanaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataLoader {

    @Bean
    CommandLineRunner seed(LanaRepository repo) {
        return args -> {
            if (repo.count() == 0) {
                repo.save(new Lana("Lana Merino", "Rojo", 4990.0));
                repo.save(new Lana("Lana Algodón", "Azul", 3990.0));
                repo.save(new Lana("Lana Acrílica", "Verde", 2990.0));
            }
        };
    }
}