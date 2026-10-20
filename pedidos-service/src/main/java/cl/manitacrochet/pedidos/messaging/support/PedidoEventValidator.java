package cl.manitacrochet.pedidos.messaging.support;

import cl.manitacrochet.pedidos.messaging.event.PedidoEvent;

/**
 * Valida el contenido de un evento recibido. Un evento inválido es un error NO recuperable:
 * reintentarlo no lo arregla, así que va directo a la DLQ.
 */
public final class PedidoEventValidator {

    private PedidoEventValidator() {}

    public static void validar(PedidoEvent evento) {
        if (evento == null) {
            throw new NonRecoverableMessageException("El mensaje no contiene un evento");
        }
        if (evento.getTipo() == null) {
            throw new NonRecoverableMessageException("Evento sin tipo");
        }
        if (evento.getPedidoId() == null) {
            throw new NonRecoverableMessageException("Evento sin pedidoId");
        }
        if (evento.getCliente() == null || evento.getCliente().isBlank()) {
            throw new NonRecoverableMessageException("Evento sin cliente");
        }
        if (evento.getCantidad() == null || evento.getCantidad() <= 0) {
            throw new NonRecoverableMessageException("Cantidad inválida: " + evento.getCantidad());
        }
    }
}
