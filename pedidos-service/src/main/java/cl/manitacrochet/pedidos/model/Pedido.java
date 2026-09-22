package cl.manitacrochet.pedidos.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String cliente;
    private Long lanaId;
    private Integer cantidad;
    private String estado;

    public Pedido() {}

    public Pedido(String cliente, Long lanaId, Integer cantidad) {
        this.cliente = cliente;
        this.lanaId = lanaId;
        this.cantidad = cantidad;
        this.estado = "PENDIENTE";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }
    public Long getLanaId() { return lanaId; }
    public void setLanaId(Long lanaId) { this.lanaId = lanaId; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
