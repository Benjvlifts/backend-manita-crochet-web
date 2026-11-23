package cl.manitacrochet.pedidos.kafka.service;

import cl.manitacrochet.pedidos.kafka.dto.LanaKafkaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Lógica de dominio: mantiene una copia local del catálogo de lanas a partir de lanas.eventos (sin llamar por HTTP a lanas-service). */
@Service
public class CatalogoLanasService {

    private static final Logger log = LoggerFactory.getLogger(CatalogoLanasService.class);

    private final Map<Long, String> nombresPorLana = new ConcurrentHashMap<>();

    public void registrar(LanaKafkaEvent evento) {
        nombresPorLana.put(evento.getLanaId(), evento.getNombre());
        log.info("[catalogo] Lana {} '{}' registrada en el catálogo local ({} lanas conocidas)",
                evento.getLanaId(), evento.getNombre(), nombresPorLana.size());
    }

    public Optional<String> nombrePorId(Long lanaId) {
        return Optional.ofNullable(nombresPorLana.get(lanaId));
    }

    public int total() {
        return nombresPorLana.size();
    }
}
