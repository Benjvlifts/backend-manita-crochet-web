package cl.manitacrochet.rabbitadmin.controller;

import cl.manitacrochet.rabbitadmin.dto.AdminResponse;
import cl.manitacrochet.rabbitadmin.dto.CrearColaRequest;
import cl.manitacrochet.rabbitadmin.exception.RecursoNoEncontradoException;
import cl.manitacrochet.rabbitadmin.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la API: seguridad (401/403), validación de entradas (400) y códigos de éxito.
 * RabbitAdminService se simula, así que no hace falta un RabbitMQ levantado ni un tenant real.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RabbitAdminControllerTest {

    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_Admin");
    private static final SimpleGrantedAuthority USER = new SimpleGrantedAuthority("ROLE_User");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RabbitAdminService service;

    // ---------- seguridad ----------

    @Test
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/api/public/health"))
                .andExpect(status().isOk());
    }

    @Test
    void crearColaSinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/api/admin/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"cola.test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearColaConRolUserDevuelve403() throws Exception {
        mockMvc.perform(post("/api/admin/queues")
                        .with(jwt().authorities(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"cola.test\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- validación de entradas (400) ----------

    @Test
    void crearColaConNombreVacioDevuelve400() throws Exception {
        mockMvc.perform(post("/api/admin/queues")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists());
    }

    @Test
    void crearColaConNombreReservadoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/admin/queues")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"amq.cola\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists());
    }

    @Test
    void crearColaConTtlNegativoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/admin/queues")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"cola.test\",\"messageTtlMs\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.messageTtlMs").exists());
    }

    @Test
    void crearExchangeConTipoInvalidoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/admin/exchanges")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"ex.test\",\"tipo\":\"inventado\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.tipo").exists());
    }

    @Test
    void crearBindingSinDestinoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/admin/bindings")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchange\":\"ex.test\",\"tipoDestino\":\"QUEUE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.destino").exists());
    }

    @Test
    void eliminarBindingSinParametrosDevuelve400() throws Exception {
        mockMvc.perform(delete("/api/admin/bindings")
                        .with(jwt().authorities(ADMIN)))
                .andExpect(status().isBadRequest());
    }

    // ---------- operaciones exitosas y errores de negocio ----------

    @Test
    void crearColaValidaDevuelve201() throws Exception {
        when(service.crearCola(any(CrearColaRequest.class)))
                .thenReturn(new AdminResponse("COLA", "cola.test", "Cola creada correctamente"));

        mockMvc.perform(post("/api/admin/queues")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"cola.test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("cola.test"));
    }

    @Test
    void eliminarColaExistenteDevuelve204() throws Exception {
        mockMvc.perform(delete("/api/admin/queues/cola.test")
                        .with(jwt().authorities(ADMIN)))
                .andExpect(status().isNoContent());
    }

    @Test
    void eliminarColaInexistenteDevuelve404() throws Exception {
        doThrow(new RecursoNoEncontradoException("La cola 'nada' no existe"))
                .when(service).eliminarCola("nada");

        mockMvc.perform(delete("/api/admin/queues/nada")
                        .with(jwt().authorities(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }
}
