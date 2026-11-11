package ru.practicum.ewm.stats.analyzer.kafka;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.analyzer.service.StatsStorageService;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.serialization.EventSimilarityDeserializer;
import ru.practicum.ewm.stats.serialization.UserActionDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Запускает два независимых потребителя Kafka (действия пользователей и коэффициенты сходства),
 * каждый в своём потоке
 */
@Slf4j
@Component
public class KafkaListenersRunner implements ApplicationRunner {

    private final StatsStorageService storage;
    private final String bootstrapServers;
    private final String userActionsTopic;
    private final String similarityTopic;
    private final String groupPrefix;

    private final List<KafkaConsumer<?, ?>> consumers = new CopyOnWriteArrayList<>();

    public KafkaListenersRunner(StatsStorageService storage,
                                @Value("${kafka.bootstrap-servers}") String bootstrapServers,
                                @Value("${kafka.topics.user-actions}") String userActionsTopic,
                                @Value("${kafka.topics.events-similarity}") String similarityTopic,
                                @Value("${kafka.consumer.group-id-prefix}") String groupPrefix) {
        this.storage = storage;
        this.bootstrapServers = bootstrapServers;
        this.userActionsTopic = userActionsTopic;
        this.similarityTopic = similarityTopic;
        this.groupPrefix = groupPrefix;
    }

    @Override
    public void run(ApplicationArguments args) {
        start("user-actions-listener", userActionsTopic, groupPrefix + "-user-actions",
                UserActionDeserializer.class, (UserActionAvro a) -> storage.saveUserAction(a));
        start("similarity-listener", similarityTopic, groupPrefix + "-similarity",
                EventSimilarityDeserializer.class, (EventSimilarityAvro s) -> storage.saveSimilarity(s));
    }

    private <T> void start(String name, String topic, String groupId,
                           Class<?> valueDeserializer, Consumer<T> handler) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        KafkaConsumer<String, T> consumer = new KafkaConsumer<>(props);
        consumers.add(consumer);
        Thread thread = new Thread(() -> poll(consumer, topic, handler), name);
        thread.start();
    }

    private <T> void poll(KafkaConsumer<String, T> consumer, String topic, Consumer<T> handler) {
        try {
            consumer.subscribe(List.of(topic));
            while (true) {
                ConsumerRecords<String, T> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, T> record : records) {
                    handler.accept(record.value());
                }
                if (!records.isEmpty()) {
                    consumer.commitSync();
                }
            }
        } catch (WakeupException ignored) {
            // штатная остановка
        } catch (Exception e) {
            log.error("Ошибка при чтении топика {}", topic, e);
        } finally {
            consumer.close();
        }
    }

    @PreDestroy
    public void stop() {
        consumers.forEach(KafkaConsumer::wakeup);
    }
}
