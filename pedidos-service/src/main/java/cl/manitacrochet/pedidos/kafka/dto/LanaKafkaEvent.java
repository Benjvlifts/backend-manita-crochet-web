package cl.manitacrochet.pedidos.kafka.dto;

/** DTO del tópico lanas.eventos (lo publica lanas-service; aquí solo se consume). La key del mensaje es el lanaId. */
public class LanaKafkaEvent {

    public enum Tipo { LANA_CREADA }

    private String eventId;
    private Tipo tipo;
    private Long lanaId;
    private String nombre;
    private String color;
    private Double precio;
    private String ocurridoEn;

    public LanaKafkaEvent() {}

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }
    public Long getLanaId() { return lanaId; }
    public void setLanaId(Long lanaId) { this.lanaId = lanaId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Double getPrecio() { return precio; }
    public void setPrecio(Double precio) { this.precio = precio; }
    public String getOcurridoEn() { return ocurridoEn; }
    public void setOcurridoEn(String ocurridoEn) { this.ocurridoEn = ocurridoEn; }

    @Override
    public String toString() {
        return "LanaKafkaEvent{eventId=" + eventId + ", tipo=" + tipo + ", lanaId=" + lanaId + ", nombre='" + nombre + "'}";
    }
}
