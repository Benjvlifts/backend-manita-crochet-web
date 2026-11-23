package cl.manitacrochet.lanas.kafka.service;

import cl.manitacrochet.lanas.kafka.dto.PedidoKafkaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lógica de dominio de la analítica: acumula las unidades vendidas por lana a partir del flujo de pedidos.
 * No sabe nada de Kafka (eso es del consumidor), así se puede probar y reutilizar por separado.
 */
@Service
public class VentasAnaliticaService {

    private static final Logger log = LoggerFactory.getLogger(VentasAnaliticaService.class);
    private static final int MAX_EVENTOS_RECORDADOS = 10_000;

    /** Resultado que expone el endpoint de analítica. */
    public record VentasSnapshot(Map<Long, Long> unidadesPorLana, long pedidosProcesados, long unidadesTotales) {
    }

    private final Map<Long, Long> unidadesPorLana = new ConcurrentHashMap<>();
    private final AtomicLong pedidosProcesados = new AtomicLong();
    private final AtomicLong unidadesTotales = new AtomicLong();

    // Kafka entrega "al menos una vez": recordar los últimos eventIds evita contar dos veces el mismo pedido.
    private final Set<String> eventosProcesados = Collections.newSetFromMap(Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_EVENTOS_RECORDADOS;
                }
            }));

    public void registrar(PedidoKafkaEvent evento) {
        if (evento.getTipo() != PedidoKafkaEvent.Tipo.PEDIDO_CREADO) {
            return; // un cambio de estado no suma ventas
        }
        if (!eventosProcesados.add(evento.getEventId())) {
            log.warn("[analitica] Evento duplicado ignorado eventId={}", evento.getEventId());
            return;
        }
        unidadesPorLana.merge(evento.getLanaId(), evento.getCantidad().longValue(), Long::sum);
        pedidosProcesados.incrementAndGet();
        unidadesTotales.addAndGet(evento.getCantidad());
        log.info("[analitica] Lana {} +{} unidades (pedido #{}); total de unidades vendidas: {}",
                evento.getLanaId(), evento.getCantidad(), evento.getPedidoId(), unidadesTotales.get());
    }

    public VentasSnapshot snapshot() {
        return new VentasSnapshot(new TreeMap<>(unidadesPorLana), pedidosProcesados.get(), unidadesTotales.get());
    }
}
