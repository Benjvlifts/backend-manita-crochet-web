package cl.manitacrochet.kafkaadmin.service;

import cl.manitacrochet.kafkaadmin.dto.AdminResponse;
import cl.manitacrochet.kafkaadmin.dto.CrearTopicoRequest;
import cl.manitacrochet.kafkaadmin.dto.GrupoDetalleResponse;
import cl.manitacrochet.kafkaadmin.dto.GrupoDetalleResponse.OffsetResponse;
import cl.manitacrochet.kafkaadmin.dto.GrupoResumenResponse;
import cl.manitacrochet.kafkaadmin.dto.TopicoDetalleResponse;
import cl.manitacrochet.kafkaadmin.dto.TopicoDetalleResponse.ParticionResponse;
import cl.manitacrochet.kafkaadmin.exception.BrokerNoDisponibleException;
import cl.manitacrochet.kafkaadmin.exception.KafkaOperacionException;
import cl.manitacrochet.kafkaadmin.exception.RecursoNoEncontradoException;
import cl.manitacrochet.kafkaadmin.exception.RecursoYaExisteException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Toda la administración de Kafka vive aquí (el controlador no conoce el AdminClient):
 * tópicos (crear, listar, describir, eliminar), sus configuraciones (modificar y restablecer),
 * particiones (aumentar) y consumer groups (listar, describir y calcular el lag).
 */
@Service
public class KafkaAdminService {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);

    private final Admin admin;
    private final long timeoutMs;

    public KafkaAdminService(Admin admin, @Value("${kafka.admin.timeout-ms:8000}") long timeoutMs) {
        this.admin = admin;
        this.timeoutMs = timeoutMs;
    }

    // ===================== Tópicos =====================

    public AdminResponse crearTopico(CrearTopicoRequest req) {
        NewTopic topico = new NewTopic(req.nombre(), req.particiones(), req.replicas().shortValue());
        if (req.configuraciones() != null && !req.configuraciones().isEmpty()) {
            topico.configs(req.configuraciones());
        }
        esperar(admin.createTopics(List.of(topico)).all(), "el tópico '" + req.nombre() + "'");
        log.info("Tópico creado nombre={} particiones={} replicas={} configs={}",
                req.nombre(), req.particiones(), req.replicas(), req.configuraciones());
        return new AdminResponse("TOPICO", req.nombre(), "Tópico creado correctamente");
    }

    public List<String> listarTopicos() {
        List<String> nombres = new ArrayList<>(
                esperar(admin.listTopics(new ListTopicsOptions().listInternal(false)).names(), "los tópicos"));
        nombres.sort(Comparator.naturalOrder());
        return nombres;
    }

    public TopicoDetalleResponse describirTopico(String nombre) {
        verificarExiste(nombre);
        TopicDescription descripcion = esperar(admin.describeTopics(List.of(nombre)).allTopicNames(),
                "el tópico '" + nombre + "'").get(nombre);

        List<ParticionResponse> particiones = descripcion.partitions().stream()
                .map(p -> new ParticionResponse(
                        p.partition(),
                        p.leader() != null ? p.leader().id() : -1,
                        p.replicas().stream().map(Node::id).toList(),
                        p.isr().stream().map(Node::id).toList()))
                .toList();
        int replicas = particiones.isEmpty() ? 0 : particiones.get(0).replicas().size();

        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        Config config = esperar(admin.describeConfigs(List.of(recurso)).all(), "la configuración de '" + nombre + "'").get(recurso);
        // Solo las configuraciones definidas explícitamente en el tópico (no los valores por defecto del broker).
        Map<String, String> explicitas = new TreeMap<>();
        for (ConfigEntry e : config.entries()) {
            if (e.source() == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG) {
                explicitas.put(e.name(), e.value());
            }
        }
        return new TopicoDetalleResponse(nombre, particiones.size(), replicas, particiones, explicitas);
    }

    public AdminResponse actualizarConfiguracion(String nombre, Map<String, String> configuraciones) {
        verificarExiste(nombre);
        Collection<AlterConfigOp> operaciones = new ArrayList<>();
        configuraciones.forEach((clave, valor) -> operaciones.add(
                new AlterConfigOp(new ConfigEntry(clave, valor), AlterConfigOp.OpType.SET)));
        alterar(nombre, operaciones);
        log.info("Configuración modificada topico={} cambios={}", nombre, configuraciones);
        return new AdminResponse("TOPICO", nombre, "Configuración actualizada: " + configuraciones.keySet());
    }

    /** Elimina el valor explícito de una configuración: el tópico vuelve al valor por defecto del broker. */
    public void restablecerConfiguracion(String nombre, String clave) {
        verificarExiste(nombre);
        Collection<AlterConfigOp> operaciones = new ArrayList<>();
        operaciones.add(new AlterConfigOp(new ConfigEntry(clave, null), AlterConfigOp.OpType.DELETE));
        alterar(nombre, operaciones);
        log.info("Configuración restablecida topico={} clave={}", nombre, clave);
    }

    public AdminResponse aumentarParticiones(String nombre, int nuevoTotal) {
        verificarExiste(nombre);
        esperar(admin.createPartitions(Map.of(nombre, NewPartitions.increaseTo(nuevoTotal))).all(),
                "las particiones de '" + nombre + "'");
        log.info("Particiones aumentadas topico={} total={}", nombre, nuevoTotal);
        return new AdminResponse("TOPICO", nombre, "El tópico ahora tiene " + nuevoTotal + " particiones");
    }

    public void eliminarTopico(String nombre) {
        if (nombre.startsWith("__")) {
            throw new IllegalArgumentException("'" + nombre + "' es un tópico interno de Kafka y no puede eliminarse");
        }
        verificarExiste(nombre);
        esperar(admin.deleteTopics(List.of(nombre)).all(), "el tópico '" + nombre + "'");
        log.info("Tópico eliminado nombre={}", nombre);
    }

    // ===================== Consumer groups =====================

    public List<GrupoResumenResponse> listarGrupos() {
        Collection<ConsumerGroupListing> grupos = esperar(admin.listConsumerGroups().all(), "los consumer groups");
        return grupos.stream()
                .map(g -> new GrupoResumenResponse(g.groupId(), g.state().map(s -> s.name()).orElse("DESCONOCIDO")))
                .sorted(Comparator.comparing(GrupoResumenResponse::id))
                .toList();
    }

    public GrupoDetalleResponse describirGrupo(String id) {
        ConsumerGroupDescription descripcion = esperar(admin.describeConsumerGroups(List.of(id)).all(),
                "el grupo '" + id + "'").get(id);
        if (descripcion == null || descripcion.state() == ConsumerGroupState.DEAD) {
            throw new RecursoNoEncontradoException("El consumer group '" + id + "' no existe");
        }

        Map<TopicPartition, OffsetAndMetadata> confirmados = esperar(
                admin.listConsumerGroupOffsets(id).partitionsToOffsetAndMetadata(), "los offsets de '" + id + "'");

        Map<TopicPartition, OffsetSpec> consulta = new HashMap<>();
        confirmados.keySet().forEach(tp -> consulta.put(tp, OffsetSpec.latest()));
        Map<TopicPartition, ListOffsetsResultInfo> finales = consulta.isEmpty()
                ? Map.of()
                : esperar(admin.listOffsets(consulta).all(), "los offsets finales de '" + id + "'");

        List<OffsetResponse> offsets = new ArrayList<>();
        long lagTotal = 0;
        for (Map.Entry<TopicPartition, OffsetAndMetadata> e : confirmados.entrySet()) {
            long confirmado = e.getValue().offset();
            long fin = finales.get(e.getKey()).offset();
            long lag = Math.max(0, fin - confirmado);
            lagTotal += lag;
            offsets.add(new OffsetResponse(e.getKey().topic(), e.getKey().partition(), confirmado, fin, lag));
        }
        offsets.sort(Comparator.comparing(OffsetResponse::topico).thenComparingInt(OffsetResponse::particion));

        return new GrupoDetalleResponse(id, descripcion.state().name(), descripcion.members().size(), offsets, lagTotal);
    }

    // ===================== Auxiliares =====================

    private void alterar(String nombre, Collection<AlterConfigOp> operaciones) {
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        Map<ConfigResource, Collection<AlterConfigOp>> cambios = Map.of(recurso, operaciones);
        esperar(admin.incrementalAlterConfigs(cambios).all(), "la configuración de '" + nombre + "'");
    }

    /** Verifica antes de operar para responder 404 con un mensaje claro en vez de un error genérico del broker. */
    private void verificarExiste(String nombre) {
        if (!listarTopicos().contains(nombre)) {
            throw new RecursoNoEncontradoException("El tópico '" + nombre + "' no existe");
        }
    }

    /** Espera el resultado de Kafka con tiempo límite y traduce los errores del broker a excepciones de la API. */
    private <T> T esperar(KafkaFuture<T> futuro, String que) {
        try {
            return futuro.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw traducir(e.getCause(), que);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaOperacionException("Operación interrumpida sobre " + que);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new BrokerNoDisponibleException("sin respuesta de los brokers al operar sobre " + que);
        }
    }

    private RuntimeException traducir(Throwable causa, String que) {
        if (causa instanceof TopicExistsException) {
            return new RecursoYaExisteException("Ya existe " + que);
        }
        if (causa instanceof UnknownTopicOrPartitionException || causa instanceof GroupIdNotFoundException) {
            return new RecursoNoEncontradoException("No existe " + que);
        }
        if (causa instanceof InvalidTopicException || causa instanceof InvalidReplicationFactorException
                || causa instanceof InvalidPartitionsException || causa instanceof InvalidConfigurationException
                || causa instanceof InvalidRequestException) {
            return new IllegalArgumentException(causa.getMessage());
        }
        if (causa instanceof org.apache.kafka.common.errors.TimeoutException) {
            return new BrokerNoDisponibleException("sin respuesta de los brokers al operar sobre " + que);
        }
        return new KafkaOperacionException("Kafka rechazó la operación sobre " + que + ": "
                + (causa != null ? causa.getMessage() : "causa desconocida"));
    }
}
