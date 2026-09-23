package cl.manitacrochet.pedidos.repository;

import cl.manitacrochet.pedidos.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
}