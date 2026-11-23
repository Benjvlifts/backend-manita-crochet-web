package cl.manitacrochet.kafkaadmin.exception;

/** Kafka rechazó la operación por una causa no prevista -> HTTP 502. */
public class KafkaOperacionException extends RuntimeException {
    public KafkaOperacionException(String message) { super(message); }
}
