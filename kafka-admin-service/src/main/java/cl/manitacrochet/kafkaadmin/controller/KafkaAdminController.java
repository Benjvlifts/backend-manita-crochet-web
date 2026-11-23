package cl.manitacrochet.kafkaadmin.controller;

import cl.manitacrochet.kafkaadmin.dto.ActualizarConfigRequest;
import cl.manitacrochet.kafkaadmin.dto.AdminResponse;
import cl.manitacrochet.kafkaadmin.dto.AumentarParticionesRequest;
import cl.manitacrochet.kafkaadmin.dto.CrearTopicoRequest;
import cl.manitacrochet.kafkaadmin.dto.GrupoDetalleResponse;
import cl.manitacrochet.kafkaadmin.dto.GrupoResumenResponse;
import cl.manitacrochet.kafkaadmin.dto.TopicoDetalleResponse;
import cl.manitacrochet.kafkaadmin.service.KafkaAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API REST de administración de Kafka (requiere rol Admin). Recursos en plural, verbo HTTP = acción:
 *
 *   POST   /api/admin/kafka/topics                       crea un tópico                          -> 201
 *   GET    /api/admin/kafka/topics                       lista los tópicos                       -> 200
 *   GET    /api/admin/kafka/topics/{nombre}              detalle: particiones y configuración    -> 200
 *   PUT    /api/admin/kafka/topics/{nombre}/config       modifica configuraciones                -> 200
 *   DELETE /api/admin/kafka/topics/{nombre}/config/{clave}  restablece una configuración         -> 204
 *   PATCH  /api/admin/kafka/topics/{nombre}/partitions   aumenta las particiones                 -> 200
 *   DELETE /api/admin/kafka/topics/{nombre}              elimina un tópico                       -> 204
 *   GET    /api/admin/kafka/consumer-groups              lista los consumer groups               -> 200
 *   GET    /api/admin/kafka/consumer-groups/{id}         estado, miembros, offsets y lag         -> 200
 *
 * Errores: 400 (validación), 404 (no existe), 409 (ya existe), 401/403 (seguridad), 503 (Kafka caído).
 * El controlador solo valida la entrada (@Valid) y delega en KafkaAdminService.
 */
@RestController
@RequestMapping("/api/admin/kafka")
public class KafkaAdminController {

    private final KafkaAdminService service;

    public KafkaAdminController(KafkaAdminService service) {
        this.service = service;
    }

    @PostMapping("/topics")
    public ResponseEntity<AdminResponse> crearTopico(@Valid @RequestBody CrearTopicoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearTopico(request));
    }

    @GetMapping("/topics")
    public List<String> listarTopicos() {
        return service.listarTopicos();
    }

    @GetMapping("/topics/{nombre}")
    public TopicoDetalleResponse describirTopico(@PathVariable String nombre) {
        return service.describirTopico(nombre);
    }

    @PutMapping("/topics/{nombre}/config")
    public AdminResponse actualizarConfiguracion(@PathVariable String nombre,
                                                 @Valid @RequestBody ActualizarConfigRequest request) {
        return service.actualizarConfiguracion(nombre, request.configuraciones());
    }

    @DeleteMapping("/topics/{nombre}/config/{clave}")
    public ResponseEntity<Void> restablecerConfiguracion(@PathVariable String nombre, @PathVariable String clave) {
        service.restablecerConfiguracion(nombre, clave);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/topics/{nombre}/partitions")
    public AdminResponse aumentarParticiones(@PathVariable String nombre,
                                             @Valid @RequestBody AumentarParticionesRequest request) {
        return service.aumentarParticiones(nombre, request.particiones());
    }

    @DeleteMapping("/topics/{nombre}")
    public ResponseEntity<Void> eliminarTopico(@PathVariable String nombre) {
        service.eliminarTopico(nombre);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/consumer-groups")
    public List<GrupoResumenResponse> listarGrupos() {
        return service.listarGrupos();
    }

    @GetMapping("/consumer-groups/{id}")
    public GrupoDetalleResponse describirGrupo(@PathVariable String id) {
        return service.describirGrupo(id);
    }
}
