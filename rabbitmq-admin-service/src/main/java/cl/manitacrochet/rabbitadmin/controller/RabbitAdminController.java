package cl.manitacrochet.rabbitadmin.controller;

import cl.manitacrochet.rabbitadmin.dto.AdminResponse;
import cl.manitacrochet.rabbitadmin.dto.ColaInfoResponse;
import cl.manitacrochet.rabbitadmin.dto.CrearBindingRequest;
import cl.manitacrochet.rabbitadmin.dto.CrearColaRequest;
import cl.manitacrochet.rabbitadmin.dto.CrearExchangeRequest;
import cl.manitacrochet.rabbitadmin.dto.EliminarBindingRequest;
import cl.manitacrochet.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST de administración de RabbitMQ. Recursos en plural, verbo HTTP = acción:
 *
 *   POST   /api/admin/queues            crea una cola          -> 201
 *   GET    /api/admin/queues/{nombre}   estado de una cola     -> 200
 *   DELETE /api/admin/queues/{nombre}   elimina una cola       -> 204
 *   POST   /api/admin/exchanges         crea un exchange       -> 201
 *   DELETE /api/admin/exchanges/{nombre} elimina un exchange   -> 204
 *   POST   /api/admin/bindings          crea un binding        -> 201
 *   DELETE /api/admin/bindings?exchange=&destino=&tipoDestino=&routingKey=  elimina un binding -> 204
 *
 * Errores: 400 (validación), 404 (no existe), 409 (ya existe), 401/403 (seguridad).
 * El controlador no conoce RabbitMQ: solo valida la entrada (@Valid) y delega en RabbitAdminService.
 */
@RestController
@RequestMapping("/api/admin")
public class RabbitAdminController {

    private final RabbitAdminService service;

    public RabbitAdminController(RabbitAdminService service) {
        this.service = service;
    }

    @PostMapping("/queues")
    public ResponseEntity<AdminResponse> crearCola(@Valid @RequestBody CrearColaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearCola(request));
    }

    @GetMapping("/queues/{nombre}")
    public ColaInfoResponse infoCola(@PathVariable String nombre) {
        return service.infoCola(nombre);
    }

    @DeleteMapping("/queues/{nombre}")
    public ResponseEntity<Void> eliminarCola(@PathVariable String nombre) {
        service.eliminarCola(nombre);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exchanges")
    public ResponseEntity<AdminResponse> crearExchange(@Valid @RequestBody CrearExchangeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearExchange(request));
    }

    @DeleteMapping("/exchanges/{nombre}")
    public ResponseEntity<Void> eliminarExchange(@PathVariable String nombre) {
        service.eliminarExchange(nombre);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bindings")
    public ResponseEntity<AdminResponse> crearBinding(@Valid @RequestBody CrearBindingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearBinding(request));
    }

    @DeleteMapping("/bindings")
    public ResponseEntity<Void> eliminarBinding(@Valid EliminarBindingRequest request) {
        service.eliminarBinding(request);
        return ResponseEntity.noContent().build();
    }
}
