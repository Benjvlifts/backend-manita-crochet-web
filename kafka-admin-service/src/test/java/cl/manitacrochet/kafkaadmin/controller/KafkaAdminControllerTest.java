package cl.manitacrochet.kafkaadmin.controller;

import cl.manitacrochet.kafkaadmin.dto.AdminResponse;
import cl.manitacrochet.kafkaadmin.dto.CrearTopicoRequest;
import cl.manitacrochet.kafkaadmin.dto.GrupoResumenResponse;
import cl.manitacrochet.kafkaadmin.dto.TopicoDetalleResponse;
import cl.manitacrochet.kafkaadmin.exception.RecursoNoEncontradoException;
import cl.manitacrochet.kafkaadmin.exception.RecursoYaExisteException;
import cl.manitacrochet.kafkaadmin.service.KafkaAdminService;
import org.apache.kafka.clients.admin.Admin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la API: seguridad (401/403), validación de entradas (400) y códigos de éxito/error.
 * KafkaAdminService y el AdminClient se simulan: no hace falta un Kafka levantado ni un tenant real.
 */
@SpringBootTest
@AutoConfigureMockMvc
class KafkaAdminControllerTest {

    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_Admin");
    private static final SimpleGrantedAuthority USER = new SimpleGrantedAuthority("ROLE_User");
    private static final String BASE = "/api/admin/kafka";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Admin admin;

    @MockBean
    private KafkaAdminService service;

    // ---------- seguridad ----------

    @Test
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/api/public/health")).andExpect(status().isOk());
    }

    @Test
    void listarTopicosSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get(BASE + "/topics")).andExpect(status().isUnauthorized());
    }

    @Test
    void listarTopicosConRolUserDevuelve403() throws Exception {
        mockMvc.perform(get(BASE + "/topics").with(jwt().authorities(USER))).andExpect(status().isForbidden());
    }

    // ---------- validación de entradas (400) ----------

    @Test
    void crearTopicoConNombreVacioDevuelve400() throws Exception {
        mockMvc.perform(post(BASE + "/topics")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"particiones\":3,\"replicas\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists());
    }

    @Test
    void crearTopicoConCaracteresInvalidosDevuelve400() throws Exception {
        mockMvc.perform(post(BASE + "/topics")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"mi topico!\",\"particiones\":3,\"replicas\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").exists());
    }

    @Test
    void crearTopicoConCeroParticionesDevuelve400() throws Exception {
        mockMvc.perform(post(BASE + "/topics")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"demo\",\"particiones\":0,\"replicas\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.particiones").exists());
    }

    @Test
    void crearTopicoSinReplicasDevuelve400() throws Exception {
        mockMvc.perform(post(BASE + "/topics")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"demo\",\"particiones\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.replicas").exists());
    }

    @Test
    void actualizarConfiguracionVaciaDevuelve400() throws Exception {
        mockMvc.perform(put(BASE + "/topics/demo/config")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configuraciones\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.configuraciones").exists());
    }

    @Test
    void aumentarParticionesConMenosDeDosDevuelve400() throws Exception {
        mockMvc.perform(patch(BASE + "/topics/demo/partitions")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"particiones\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.particiones").exists());
    }

    // ---------- operaciones exitosas y errores de negocio ----------

    @Test
    void crearTopicoValidoDevuelve201() throws Exception {
        when(service.crearTopico(any(CrearTopicoRequest.class)))
                .thenReturn(new AdminResponse("TOPICO", "demo", "Tópico creado correctamente"));

        mockMvc.perform(post(BASE + "/topics")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"demo\",\"particiones\":3,\"replicas\":1,"
                                + "\"configuraciones\":{\"retention.ms\":\"86400000\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("demo"));
    }

    @Test
    void crearTopicoQueYaExisteDevuelve409() throws Exception {
        when(service.crearTopico(any(CrearTopicoRequest.class)))
                .thenThrow(new RecursoYaExisteException("Ya existe el tópico 'demo'"));

        mockMvc.perform(post(BASE + "/topics")
                        .with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"demo\",\"particiones\":3,\"replicas\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"));
    }

    @Test
    void listarTopicosDevuelve200() throws Exception {
        when(service.listarTopicos()).thenReturn(List.of("lanas.eventos", "pedidos.eventos"));

        mockMvc.perform(get(BASE + "/topics").with(jwt().authorities(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("lanas.eventos"));
    }

    @Test
    void describirTopicoDevuelve200() throws Exception {
        when(service.describirTopico("demo"))
                .thenReturn(new TopicoDetalleResponse("demo", 3, 1, List.of(), Map.of("retention.ms", "86400000")));

        mockMvc.perform(get(BASE + "/topics/demo").with(jwt().authorities(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.particiones").value(3));
    }

    @Test
    void describirTopicoInexistenteDevuelve404() throws Exception {
        when(service.describirTopico("nada")).thenThrow(new RecursoNoEncontradoException("El tópico 'nada' no existe"));

        mockMvc.perform(get(BASE + "/topics/nada").with(jwt().authorities(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void eliminarTopicoDevuelve204() throws Exception {
        mockMvc.perform(delete(BASE + "/topics/demo").with(jwt().authorities(ADMIN)))
                .andExpect(status().isNoContent());
    }

    @Test
    void restablecerConfiguracionDevuelve204() throws Exception {
        mockMvc.perform(delete(BASE + "/topics/demo/config/retention.ms").with(jwt().authorities(ADMIN)))
                .andExpect(status().isNoContent());
    }

    @Test
    void listarConsumerGroupsDevuelve200() throws Exception {
        when(service.listarGrupos()).thenReturn(List.of(new GrupoResumenResponse("lanas-analitica", "STABLE")));

        mockMvc.perform(get(BASE + "/consumer-groups").with(jwt().authorities(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("lanas-analitica"));
    }
}
