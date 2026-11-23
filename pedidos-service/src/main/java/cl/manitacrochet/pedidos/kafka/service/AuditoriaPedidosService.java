package cl.manitacrochet.pedidos.kafka.service;

import cl.manitacrochet.pedidos.kafka.dto.PedidoKafkaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Lógica de dominio: bitácora de auditoría con los últimos eventos de pedidos recibidos desde Kafka. */
@Service
public class AuditoriaPedidosService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaPedidosService.class);
    private static final int MAXIMO = 100;

    private final Deque<PedidoKafkaEvent> bitacora = new ArrayDeque<>();

    public synchronized void registrar(PedidoKafkaEvent evento) {
        if (bitacora.size() >= MAXIMO) {
            bitacora.removeFirst();
        }
        bitacora.addLast(evento);
        log.info("[auditoria] {} pedido #{} cliente='{}' estado={}", evento.getTipo(), evento.getPedidoId(),
                evento.getCliente(), evento.getEstado());
    }

    public synchronized List<PedidoKafkaEvent> ultimos() {
        return new ArrayList<>(bitacora);
    }
}
