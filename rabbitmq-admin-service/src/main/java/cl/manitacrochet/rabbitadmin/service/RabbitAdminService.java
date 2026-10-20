package cl.manitacrochet.rabbitadmin.service;

import cl.manitacrochet.rabbitadmin.dto.AdminResponse;
import cl.manitacrochet.rabbitadmin.dto.ColaInfoResponse;
import cl.manitacrochet.rabbitadmin.dto.CrearBindingRequest;
import cl.manitacrochet.rabbitadmin.dto.CrearColaRequest;
import cl.manitacrochet.rabbitadmin.dto.CrearExchangeRequest;
import cl.manitacrochet.rabbitadmin.dto.EliminarBindingRequest;
import cl.manitacrochet.rabbitadmin.exception.RecursoNoEncontradoException;
import cl.manitacrochet.rabbitadmin.exception.RecursoYaExisteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Toda la lógica de administración del broker vive aquí (pauta: "la lógica para crear colas y
 * exchanges está encapsulada en un servicio dedicado"). El controlador solo llama a estos métodos
 * de alto nivel y no conoce nada de RabbitMQ; RabbitAdmin no se usa en ningún otro lugar.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);
    private static final String PREFIJO_RESERVADO = "amq.";

    private final RabbitAdmin rabbitAdmin;

    public RabbitAdminService(RabbitAdmin rabbitAdmin) {
        this.rabbitAdmin = rabbitAdmin;
    }

    // ===================== Colas =====================

    public AdminResponse crearCola(CrearColaRequest req) {
        if (existeCola(req.nombre())) {
            throw new RecursoYaExisteException("La cola '" + req.nombre() + "' ya existe");
        }
        Map<String, Object> args = new HashMap<>();
        if (hayTexto(req.deadLetterExchange())) {
            args.put("x-dead-letter-exchange", req.deadLetterExchange());
        }
        if (hayTexto(req.deadLetterRoutingKey())) {
            args.put("x-dead-letter-routing-key", req.deadLetterRoutingKey());
        }
        if (req.messageTtlMs() != null) {
            args.put("x-message-ttl", req.messageTtlMs());
        }
        boolean durable = req.durable() == null || req.durable();
        boolean autoDelete = Boolean.TRUE.equals(req.autoDelete());

        rabbitAdmin.declareQueue(new Queue(req.nombre(), durable, false, autoDelete, args));
        log.info("Cola creada nombre={} durable={} autoDelete={} args={}", req.nombre(), durable, autoDelete, args);
        return new AdminResponse("COLA", req.nombre(), "Cola creada correctamente");
    }

    public ColaInfoResponse infoCola(String nombre) {
        QueueInformation info = rabbitAdmin.getQueueInfo(nombre);
        if (info == null) {
            throw new RecursoNoEncontradoException("La cola '" + nombre + "' no existe");
        }
        return new ColaInfoResponse(info.getName(), info.getMessageCount(), info.getConsumerCount());
    }

    public void eliminarCola(String nombre) {
        validarNoReservado(nombre);
        if (!existeCola(nombre)) {
            throw new RecursoNoEncontradoException("La cola '" + nombre + "' no existe");
        }
        rabbitAdmin.deleteQueue(nombre);
        log.info("Cola eliminada nombre={}", nombre);
    }

    // ===================== Exchanges =====================

    public AdminResponse crearExchange(CrearExchangeRequest req) {
        if (existeExchange(req.nombre())) {
            throw new RecursoYaExisteException("El exchange '" + req.nombre() + "' ya existe");
        }
        String tipo = req.tipo().toLowerCase(Locale.ROOT);
        boolean durable = req.durable() == null || req.durable();
        boolean autoDelete = Boolean.TRUE.equals(req.autoDelete());

        rabbitAdmin.declareExchange(new CustomExchange(req.nombre(), tipo, durable, autoDelete));
        log.info("Exchange creado nombre={} tipo={} durable={} autoDelete={}", req.nombre(), tipo, durable, autoDelete);
        return new AdminResponse("EXCHANGE", req.nombre(), "Exchange creado correctamente");
    }

    public void eliminarExchange(String nombre) {
        validarNoReservado(nombre);
        if (!existeExchange(nombre)) {
            throw new RecursoNoEncontradoException("El exchange '" + nombre + "' no existe");
        }
        rabbitAdmin.deleteExchange(nombre);
        log.info("Exchange eliminado nombre={}", nombre);
    }

    // ===================== Bindings =====================

    public AdminResponse crearBinding(CrearBindingRequest req) {
        Binding binding = construirBinding(req.exchange(), req.destino(), req.tipoDestino(), req.routingKey());
        rabbitAdmin.declareBinding(binding);
        log.info("Binding creado {}", describir(binding));
        return new AdminResponse("BINDING", describir(binding), "Binding creado correctamente");
    }

    public void eliminarBinding(EliminarBindingRequest req) {
        Binding binding = construirBinding(req.exchange(), req.destino(), req.tipoDestino(), req.routingKey());
        rabbitAdmin.removeBinding(binding);
        log.info("Binding eliminado {}", describir(binding));
    }

    // ===================== Auxiliares =====================

    /** Verifica que origen y destino existan antes de tocar el broker, para responder 404 en vez de un error de canal. */
    private Binding construirBinding(String exchange, String destino, String tipoDestino, String routingKey) {
        if (!existeExchange(exchange)) {
            throw new RecursoNoEncontradoException("El exchange de origen '" + exchange + "' no existe");
        }
        boolean haciaCola = "QUEUE".equalsIgnoreCase(tipoDestino);
        boolean destinoExiste = haciaCola ? existeCola(destino) : existeExchange(destino);
        if (!destinoExiste) {
            throw new RecursoNoEncontradoException(
                    (haciaCola ? "La cola" : "El exchange") + " de destino '" + destino + "' no existe");
        }
        String key = routingKey == null ? "" : routingKey;
        return new Binding(destino, haciaCola ? DestinationType.QUEUE : DestinationType.EXCHANGE,
                exchange, key, new HashMap<>());
    }

    private String describir(Binding b) {
        return b.getExchange() + " -> " + b.getDestination() + " [routingKey='" + b.getRoutingKey() + "']";
    }

    private boolean existeCola(String nombre) {
        return rabbitAdmin.getQueueInfo(nombre) != null;
    }

    /** No hay una consulta directa para exchanges: se hace una declaración pasiva (solo comprueba, no crea). */
    private boolean existeExchange(String nombre) {
        try {
            return Boolean.TRUE.equals(rabbitAdmin.getRabbitTemplate().execute(channel -> {
                channel.exchangeDeclarePassive(nombre);
                return Boolean.TRUE;
            }));
        } catch (AmqpConnectException e) {
            throw e;
        } catch (AmqpException e) {
            return false;
        }
    }

    private void validarNoReservado(String nombre) {
        if (nombre == null || nombre.isBlank() || nombre.startsWith(PREFIJO_RESERVADO)) {
            throw new IllegalArgumentException(
                    "'" + nombre + "' es un nombre reservado del sistema y no puede eliminarse");
        }
    }

    private boolean hayTexto(String s) {
        return s != null && !s.isBlank();
    }
}
