package cl.manitacrochet.pedidos.controller;

import cl.manitacrochet.pedidos.messaging.event.PedidoEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas básicas: simulan un JWT ya validado con distintas authorities (spring-security-test),
 * igual que en lanas-service. No dependen de red, del tenant real ni de un RabbitMQ levantado:
 * RabbitTemplate se reemplaza por un mock y los listeners no se inician.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void healthEsPublicoSinToken() throws Exception {
        mockMvc.perform(get("/api/public/health"))
                .andExpect(status().isOk());
    }

    @Test
    void listarSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/pedidos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listarConTokenValidoDevuelve200() throws Exception {
        mockMvc.perform(get("/api/pedidos").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void crearConTokenValidoDevuelve201() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"Test\",\"lanaId\":1,\"cantidad\":3}"))
                .andExpect(status().isCreated());
    }

    @Test
    void actualizarEstadoSinRolAdminDevuelve403() throws Exception {
        mockMvc.perform(patch("/api/pedidos/1/estado")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ENVIADO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearPedidoPublicaEventosDeNotificacionYTicket() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"Test\",\"lanaId\":1,\"cantidad\":3}"))
                .andExpect(status().isCreated());

        // Un evento hacia la cola de notificaciones y otro hacia la de tickets.
        verify(rabbitTemplate, times(2)).convertAndSend(anyString(), anyString(), any(PedidoEvent.class));
    }

    @Test
    void actualizarEstadoConRolAdminPublicaEvento() throws Exception {
        mockMvc.perform(patch("/api/pedidos/1/estado")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ENVIADO\"}"))
                .andExpect(status().isOk());

        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(PedidoEvent.class));
    }
}
