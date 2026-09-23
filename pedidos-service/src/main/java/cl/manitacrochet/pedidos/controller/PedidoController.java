package cl.manitacrochet.pedidos.controller;

import cl.manitacrochet.pedidos.model.Pedido;
import cl.manitacrochet.pedidos.repository.PedidoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PedidoController {

    private final PedidoRepository repository;

    public PedidoController(PedidoRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/public/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    // Cualquier usuario autenticado (Admin o User) puede ver los pedidos.
    @GetMapping("/pedidos")
    public List<Pedido> listar() {
        return repository.findAll();
    }

    // Cualquier usuario autenticado puede crear su propio pedido.
    @PostMapping("/pedidos")
    public ResponseEntity<Pedido> crear(@RequestBody Pedido nuevoPedido) {
        Pedido pedido = new Pedido(nuevoPedido.getCliente(), nuevoPedido.getLanaId(), nuevoPedido.getCantidad());
        Pedido guardado = repository.save(pedido);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }

    // Protegido a nivel de SecurityConfig con hasAuthority("ROLE_Admin"): solo Admin cambia el estado.
    @PatchMapping("/pedidos/{id}/estado")
    public ResponseEntity<Pedido> actualizarEstado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return repository.findById(id)
                .map(pedido -> {
                    pedido.setEstado(body.getOrDefault("estado", pedido.getEstado()));
                    return ResponseEntity.ok(repository.save(pedido));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}