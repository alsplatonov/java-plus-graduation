package ru.practicum.ewm.stats.aggregator;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Читает действия пользователей из Kafka, пересчитывает сходство мероприятий
 * и публикует результат в топик коэффициентов сходства
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationStarter implements CommandLineRunner {

    private final KafkaConsumer<String, UserActionAvro> consumer;
    private final KafkaProducer<String, SpecificRecordBase> producer;
    private final SimilarityCalculator calculator;

    @Value("${kafka.topics.user-actions}")
    private String userActionsTopic;

    @Value("${kafka.topics.events-similarity}")
    private String similarityTopic;

    @Override
    public void run(String... args) {
        // цикл чтения выполняется в отдельном потоке, чтобы запуск приложения мог завершиться
        new Thread(this::consumeLoop, "aggregation-loop").start();
    }

    @PreDestroy
    public void stop() {
        consumer.wakeup();
    }

    private void consumeLoop() {
        try {
            consumer.subscribe(List.of(userActionsTopic));
            while (true) {
                ConsumerRecords<String, UserActionAvro> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, UserActionAvro> record : records) {
                    handle(record.value());
                }
                if (!records.isEmpty()) {
                    consumer.commitSync();
                }
            }
        } catch (WakeupException ignored) {
            // штатная остановка
        } catch (Exception e) {
            log.error("Ошибка во время обработки действий пользователей", e);
        } finally {
            // offset подтверждается только в цикле после успешной обработки пакета сообщений
            try {
                producer.flush();
            } finally {
                log.info("Закрываем consumer и producer");
                consumer.close();
                producer.close(Duration.ofSeconds(5));
            }
        }
    }

    private void handle(UserActionAvro action) throws ExecutionException, InterruptedException {
        // все сообщения по одному действию отправляются пакетом: сначала send, затем один flush
        List<Future<RecordMetadata>> sent = new ArrayList<>();
        for (EventSimilarityAvro similarity : calculator.update(action)) {
            sent.add(producer.send(new ProducerRecord<>(similarityTopic, null,
                    similarity.getTimestamp().toEpochMilli(), null, similarity)));
            log.debug("Сходство {} и {}: {}", similarity.getEventA(), similarity.getEventB(), similarity.getScore());
        }
        producer.flush();
        for (Future<RecordMetadata> future : sent) {
            future.get();
        }
    }
}
