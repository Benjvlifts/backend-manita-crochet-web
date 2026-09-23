package cl.manitacrochet.lanas.controller;

import cl.manitacrochet.lanas.model.Lana;
import cl.manitacrochet.lanas.repository.LanaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LanaController {

    private final LanaRepository repository;

    public LanaController(LanaRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/public/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/lanas")
    public List<Lana> listar() {
        return repository.findAll();
    }
}