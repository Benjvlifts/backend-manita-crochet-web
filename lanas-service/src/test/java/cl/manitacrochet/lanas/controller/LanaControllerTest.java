package cl.manitacrochet.lanas.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas básicas del BFF: no usan un JWT real de Azure AD (no dependen de red ni del tenant),
 * sino que simulan un JWT ya validado con distintas authorities usando spring-security-test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LanaControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
}
