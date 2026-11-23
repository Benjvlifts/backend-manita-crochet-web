package cl.manitacrochet.kafkaadmin.service;

import cl.manitacrochet.kafkaadmin.dto.CrearTopicoRequest;
import cl.manitacrochet.kafkaadmin.exception.RecursoNoEncontradoException;
import cl.manitacrochet.kafkaadmin.exception.RecursoYaExisteException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaAdminServiceTest {

    @Mock
    private Admin admin;
    @Mock
    private CreateTopicsResult resultadoCrear;
    @Mock
    private ListTopicsResult resultadoListar;

    private KafkaAdminService servicio() {
        return new KafkaAdminService(admin, 2000);
    }

    @SuppressWarnings("unchecked")
    @Test
    void crearTopicoEnviaParticionesReplicasYConfiguraciones() {
        when(admin.createTopics(any(Collection.class))).thenReturn(resultadoCrear);
        when(resultadoCrear.all()).thenReturn(KafkaFuture.completedFuture(null));

        servicio().crearTopico(new CrearTopicoRequest("demo", 3, 2, Map.of("retention.ms", "1000")));

        ArgumentCaptor<Collection<NewTopic>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(admin).createTopics(captor.capture());
        NewTopic enviado = captor.getValue().iterator().next();
        assertEquals("demo", enviado.name());
        assertEquals(3, enviado.numPartitions());
        assertEquals(2, enviado.replicationFactor());
        assertEquals("1000", enviado.configs().get("retention.ms"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void crearTopicoQueYaExisteLanzaConflicto() {
        KafkaFutureImpl<Void> fallido = new KafkaFutureImpl<>();
        fallido.completeExceptionally(new TopicExistsException("ya existe"));
        when(admin.createTopics(any(Collection.class))).thenReturn(resultadoCrear);
        when(resultadoCrear.all()).thenReturn(fallido);

        assertThrows(RecursoYaExisteException.class,
                () -> servicio().crearTopico(new CrearTopicoRequest("demo", 1, 1, null)));
    }

    @Test
    void eliminarTopicoInexistenteLanzaNoEncontrado() {
        when(admin.listTopics(any(ListTopicsOptions.class))).thenReturn(resultadoListar);
        when(resultadoListar.names()).thenReturn(KafkaFuture.completedFuture(Set.of("otro")));

        assertThrows(RecursoNoEncontradoException.class, () -> servicio().eliminarTopico("nada"));
        verify(admin, never()).deleteTopics(any(Collection.class));
    }

    @Test
    void noSePuedeEliminarUnTopicoInterno() {
        assertThrows(IllegalArgumentException.class, () -> servicio().eliminarTopico("__consumer_offsets"));
    }

    @Test
    void listarTopicosDevuelveLosNombresOrdenados() {
        when(admin.listTopics(any(ListTopicsOptions.class))).thenReturn(resultadoListar);
        when(resultadoListar.names()).thenReturn(KafkaFuture.completedFuture(Set.of("pedidos.eventos", "lanas.eventos")));

        assertEquals(List.of("lanas.eventos", "pedidos.eventos"), servicio().listarTopicos());
    }
}
