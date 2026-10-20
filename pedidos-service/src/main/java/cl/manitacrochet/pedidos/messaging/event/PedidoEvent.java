package cl.manitacrochet.pedidos.messaging.event;

import cl.manitacrochet.pedidos.model.Pedido;

import java.time.Instant;

/**
 * Evento de dominio que viaja por RabbitMQ (serializado como JSON).
 * Un único contrato para los dos hechos relevantes de un pedido: fue creado o cambió de estado.
 */
public class PedidoEvent {

    public enum Tipo { PEDIDO_CREADO, PEDIDO_ESTADO_ACTUALIZADO }

    private Tipo tipo;
    private Long pedidoId;
    private String cliente;
    private Long lanaId;
    private Integer cantidad;
    private String estado;
    private Instant ocurridoEn;

    public PedidoEvent() {}

    public static PedidoEvent de(Tipo tipo, Pedido pedido) {
        PedidoEvent e = new PedidoEvent();
        e.tipo = tipo;
        e.pedidoId = pedido.getId();
        e.cliente = pedido.getCliente();
        e.lanaId = pedido.getLanaId();
        e.cantidad = pedido.getCantidad();
        e.estado = pedido.getEstado();
        e.ocurridoEn = Instant.now();
        return e;
    }

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
    public Instant getOcurridoEn() { return ocurridoEn; }
    public void setOcurridoEn(Instant ocurridoEn) { this.ocurridoEn = ocurridoEn; }

    @Override
    public String toString() {
        return "PedidoEvent{tipo=" + tipo + ", pedidoId=" + pedidoId + ", cliente='" + cliente
                + "', lanaId=" + lanaId + ", cantidad=" + cantidad + ", estado='" + estado + "'}";
    }
}
