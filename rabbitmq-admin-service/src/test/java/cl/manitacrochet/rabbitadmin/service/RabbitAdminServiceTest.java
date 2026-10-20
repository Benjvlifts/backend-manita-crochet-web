package cl.manitacrochet.rabbitadmin.service;

import cl.manitacrochet.rabbitadmin.dto.CrearColaRequest;
import cl.manitacrochet.rabbitadmin.exception.RecursoNoEncontradoException;
import cl.manitacrochet.rabbitadmin.exception.RecursoYaExisteException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitAdminServiceTest {

    @Mock
    private RabbitAdmin rabbitAdmin;

    @Test
    void crearColaConDeadLetterDeclaraLosArgumentos() {
        RabbitAdminService service = new RabbitAdminService(rabbitAdmin);
        when(rabbitAdmin.getQueueInfo("cola.nueva")).thenReturn(null);

        service.crearCola(new CrearColaRequest("cola.nueva", null, null, "mi.dlx", "mi.dlq.key", 5000L));

        ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
        verify(rabbitAdmin).declareQueue(captor.capture());
        Queue cola = captor.getValue();
        assertEquals("cola.nueva", cola.getName());
        assertTrue(cola.isDurable());
        assertEquals("mi.dlx", cola.getArguments().get("x-dead-letter-exchange"));
        assertEquals("mi.dlq.key", cola.getArguments().get("x-dead-letter-routing-key"));
        assertEquals(5000L, cola.getArguments().get("x-message-ttl"));
    }

    @Test
    void crearColaQueYaExisteLanzaConflicto() {
        RabbitAdminService service = new RabbitAdminService(rabbitAdmin);
        when(rabbitAdmin.getQueueInfo("cola.existente")).thenReturn(new QueueInformation("cola.existente", 0, 0));

        assertThrows(RecursoYaExisteException.class, () ->
                service.crearCola(new CrearColaRequest("cola.existente", null, null, null, null, null)));
        verify(rabbitAdmin, never()).declareQueue(any(Queue.class));
    }

    @Test
    void eliminarColaInexistenteLanzaNoEncontrado() {
        RabbitAdminService service = new RabbitAdminService(rabbitAdmin);
        when(rabbitAdmin.getQueueInfo("nada")).thenReturn(null);

        assertThrows(RecursoNoEncontradoException.class, () -> service.eliminarCola("nada"));
        verify(rabbitAdmin, never()).deleteQueue("nada");
    }

    @Test
    void eliminarColaExistenteLaBorra() {
        RabbitAdminService service = new RabbitAdminService(rabbitAdmin);
        when(rabbitAdmin.getQueueInfo("cola.vieja")).thenReturn(new QueueInformation("cola.vieja", 0, 0));

        service.eliminarCola("cola.vieja");

        verify(rabbitAdmin).deleteQueue("cola.vieja");
    }

    @Test
    void noSePuedeEliminarUnaColaReservada() {
        RabbitAdminService service = new RabbitAdminService(rabbitAdmin);

        assertThrows(IllegalArgumentException.class, () -> service.eliminarCola("amq.gen-xyz"));
    }
}
