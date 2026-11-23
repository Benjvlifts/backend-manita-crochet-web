package cl.manitacrochet.kafkaadmin.exception;

/** Los brokers no respondieron a tiempo -> HTTP 503. */
public class BrokerNoDisponibleException extends RuntimeException {
    public BrokerNoDisponibleException(String message) { super(message); }
}
