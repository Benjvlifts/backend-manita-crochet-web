package cl.manitacrochet.lanas.controller;

import cl.manitacrochet.lanas.kafka.producer.LanaKafkaProducer;
import cl.manitacrochet.lanas.model.Lana;
import cl.manitacrochet.lanas.repository.LanaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LanaController {

    private final LanaRepository repository;
    private final LanaKafkaProducer kafkaProducer;

    public LanaController(LanaRepository repository, LanaKafkaProducer kafkaProducer) {
        this.repository = repository;
        this.kafkaProducer = kafkaProducer;
    }

    @GetMapping("/public/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    // Cualquier usuario autenticado (Admin o User) puede ver el catálogo.
    @GetMapping("/lanas")
    public List<Lana> listar() {
        return repository.findAll();
    }

    // Protegido a nivel de SecurityConfig con hasAuthority("ROLE_Admin"): solo Admin agrega lanas.
    @PostMapping("/lanas")
    public ResponseEntity<Lana> crear(@RequestBody Lana nuevaLana) {
        Lana lana = new Lana(nuevaLana.getNombre(), nuevaLana.getColor(), nuevaLana.getPrecio());
        Lana guardada = repository.save(lana);
        // EP5: publica el hecho en Kafka; si Kafka falla, la lana igual queda creada.
        kafkaProducer.publicarLanaCreada(guardada);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardada);
    }
}