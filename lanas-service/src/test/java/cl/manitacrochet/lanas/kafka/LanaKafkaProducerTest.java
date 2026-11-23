package cl.manitacrochet.lanas.kafka;

import cl.manitacrochet.lanas.kafka.dto.LanaKafkaEvent;
import cl.manitacrochet.lanas.kafka.producer.LanaKafkaProducer;
import cl.manitacrochet.lanas.model.Lana;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanaKafkaProducerTest {

    @Mock
    private KafkaTemplate<String, LanaKafkaEvent> kafkaTemplate;

    @Test
    void lanaCreadaSePublicaEnElTopicoConfigurado() {
        when(kafkaTemplate.send(anyString(), anyString(), any(LanaKafkaEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        LanaKafkaProducer producer = new LanaKafkaProducer(kafkaTemplate, "topico.test");

        producer.publicarLanaCreada(new Lana("Merino", "Azul", 4500.0));

        ArgumentCaptor<LanaKafkaEvent> captor = ArgumentCaptor.forClass(LanaKafkaEvent.class);
        verify(kafkaTemplate).send(eq("topico.test"), anyString(), captor.capture());
        assertEquals(LanaKafkaEvent.Tipo.LANA_CREADA, captor.getValue().getTipo());
        assertEquals("Merino", captor.getValue().getNombre());
    }

    @Test
    void siKafkaFallaNoSePropagaLaExcepcion() {
        when(kafkaTemplate.send(anyString(), anyString(), any(LanaKafkaEvent.class)))
                .thenThrow(new KafkaException("broker caído"));
        LanaKafkaProducer producer = new LanaKafkaProducer(kafkaTemplate, "topico.test");

        assertDoesNotThrow(() -> producer.publicarLanaCreada(new Lana("Merino", "Azul", 4500.0)));
    }
}
