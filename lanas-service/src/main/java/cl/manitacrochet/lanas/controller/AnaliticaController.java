package cl.manitacrochet.lanas.controller;

import cl.manitacrochet.lanas.kafka.service.VentasAnaliticaService;
import cl.manitacrochet.lanas.kafka.service.VentasAnaliticaService.VentasSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Expone el resultado de la analítica que se calcula en tiempo real desde el tópico pedidos.eventos. */
@RestController
@RequestMapping("/api/analitica")
public class AnaliticaController {

    private final VentasAnaliticaService analitica;

    public AnaliticaController(VentasAnaliticaService analitica) {
        this.analitica = analitica;
    }

    // Cualquier usuario autenticado puede consultarla (cae en anyRequest().authenticated()).
    @GetMapping("/ventas")
    public VentasSnapshot ventas() {
        return analitica.snapshot();
    }
}
