package ru.practicum.ewm.stats.collector;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Корректное завершение продюсера: сначала дожидаемся отправки накопленных сообщений, затем закрываем
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaProducerShutdown {

    private final KafkaProducer<String, SpecificRecordBase> producer;

    @PreDestroy
    public void shutdown() {
        log.info("Отправляем накопленные сообщения и закрываем Kafka producer");
        try {
            producer.flush();
        } finally {
            producer.close(Duration.ofSeconds(5));
        }
    }
}
