package cl.manitacrochet.pedidos.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas básicas: simulan un JWT ya validado con distintas authorities (spring-security-test),
 * igual que en lanas-service. No dependen de red ni del tenant real.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
}
