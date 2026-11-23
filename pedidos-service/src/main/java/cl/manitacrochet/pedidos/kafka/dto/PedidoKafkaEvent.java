package cl.manitacrochet.pedidos.kafka.dto;

import cl.manitacrochet.pedidos.model.Pedido;

import java.time.Instant;
import java.util.UUID;

/** DTO del tópico pedidos.eventos (JSON). La key del mensaje es el pedidoId: así los eventos de un pedido conservan su orden. */
public class PedidoKafkaEvent {

    public enum Tipo { PEDIDO_CREADO, PEDIDO_ESTADO_ACTUALIZADO }

    private String eventId;
    private Tipo tipo;
    private Long pedidoId;
    private String cliente;
    private Long lanaId;
    private Integer cantidad;
    private String estado;
    private String ocurridoEn;

    public PedidoKafkaEvent() {}

    public static PedidoKafkaEvent de(Tipo tipo, Pedido pedido) {
        PedidoKafkaEvent e = new PedidoKafkaEvent();
        e.eventId = UUID.randomUUID().toString();
        e.tipo = tipo;
        e.pedidoId = pedido.getId();
        e.cliente = pedido.getCliente();
        e.lanaId = pedido.getLanaId();
        e.cantidad = pedido.getCantidad();
        e.estado = pedido.getEstado();
        e.ocurridoEn = Instant.now().toString();
        return e;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }
    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }
    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }
    public Long getLanaId() { return lanaId; }
    public void setLanaId(Long lanaId) { this.lanaId = lanaId; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getOcurridoEn() { return ocurridoEn; }
    public void setOcurridoEn(String ocurridoEn) { this.ocurridoEn = ocurridoEn; }

    @Override
    public String toString() {
        return "PedidoKafkaEvent{eventId=" + eventId + ", tipo=" + tipo + ", pedidoId=" + pedidoId
                + ", cliente='" + cliente + "', lanaId=" + lanaId + ", cantidad=" + cantidad + ", estado='" + estado + "'}";
    }
}
