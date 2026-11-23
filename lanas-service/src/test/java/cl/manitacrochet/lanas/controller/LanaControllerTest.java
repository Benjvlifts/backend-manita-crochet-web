package cl.manitacrochet.lanas.controller;

import cl.manitacrochet.lanas.kafka.dto.LanaKafkaEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas básicas del BFF: no usan un JWT real de Azure AD (no dependen de red ni del tenant),
 * sino que simulan un JWT ya validado con distintas authorities usando spring-security-test.
 */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"})
@AutoConfigureMockMvc
class LanaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Sin brokers en las pruebas: Kafka se simula y los listeners no se inician.
    @MockBean
    private KafkaTemplate<String, LanaKafkaEvent> kafkaTemplate;

    @Test
    void healthEsPublicoSinToken() throws Exception {
        mockMvc.perform(get("/api/public/health"))
                .andExpect(status().isOk());
    }

    @Test
    void listarSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/lanas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listarConTokenValidoDevuelve200() throws Exception {
        mockMvc.perform(get("/api/lanas").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void crearSinRolAdminDevuelve403() throws Exception {
        mockMvc.perform(post("/api/lanas")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_User")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Lana Test\",\"color\":\"Negro\",\"precio\":1000}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearConRolAdminDevuelve201() throws Exception {
        mockMvc.perform(post("/api/lanas")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Lana Test\",\"color\":\"Negro\",\"precio\":1000}"))
                .andExpect(status().isCreated());
    }

    @Test
    void crearLanaConRolAdminPublicaEventoEnKafka() throws Exception {
        mockMvc.perform(post("/api/lanas")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Merino\",\"color\":\"Azul\",\"precio\":4500}"))
                .andExpect(status().isCreated());

        verify(kafkaTemplate).send(anyString(), anyString(), any(LanaKafkaEvent.class));
    }

    @Test
    void consultarAnaliticaSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/analitica/ventas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consultarAnaliticaConTokenDevuelve200() throws Exception {
        mockMvc.perform(get("/api/analitica/ventas").with(jwt()))
                .andExpect(status().isOk());
    }
}
