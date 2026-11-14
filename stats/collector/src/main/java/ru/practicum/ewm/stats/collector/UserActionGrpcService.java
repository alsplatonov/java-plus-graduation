package ru.practicum.ewm.stats.collector;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.springframework.beans.factory.annotation.Value;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.grpc.action.ActionTypeProto;
import ru.practicum.ewm.stats.grpc.action.UserActionProto;
import ru.practicum.ewm.stats.grpc.collector.UserActionControllerGrpc;

import java.time.Instant;
import java.util.concurrent.ExecutionException;

/**
 * gRPC-сервис приёма действий пользователей. Преобразует сообщение в Avro и отправляет в Kafka
 */
@Slf4j
@GrpcService
public class UserActionGrpcService extends UserActionControllerGrpc.UserActionControllerImplBase {

    private final KafkaProducer<String, SpecificRecordBase> producer;
    private final String topic;

    public UserActionGrpcService(KafkaProducer<String, SpecificRecordBase> producer,
                                 @Value("${kafka.topics.user-actions}") String topic) {
        this.producer = producer;
        this.topic = topic;
    }

    @Override
    public void collectUserAction(UserActionProto request, StreamObserver<Empty> responseObserver) {
        try {
            UserActionAvro avro = UserActionAvro.newBuilder()
                    .setUserId(request.getUserId())
                    .setEventId(request.getEventId())
                    .setActionType(toAvro(request.getActionType()))
                    .setTimestamp(toInstant(request.getTimestamp()))
                    .build();
            producer.send(new ProducerRecord<>(topic, null, avro.getTimestamp().toEpochMilli(),
                    null, avro)).get();
            log.info("Действие {} пользователя {} над событием {} отправлено в {}",
                    avro.getActionType(), avro.getUserId(), avro.getEventId(), topic);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            // неизвестный тип действия - ошибка входных данных, а не сбой Collector
            log.warn("Некорректное действие пользователя: {}", e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e)
                    .asRuntimeException());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
        } catch (ExecutionException | KafkaException e) {
            log.error("Kafka недоступна, действие пользователя не отправлено", e);
            responseObserver.onError(Status.UNAVAILABLE.withDescription("Kafka недоступна").withCause(e)
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Не удалось обработать действие пользователя", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
        }
    }

    private static ActionTypeAvro toAvro(ActionTypeProto type) {
        return switch (type) {
            case ACTION_VIEW -> ActionTypeAvro.VIEW;
            case ACTION_REGISTER -> ActionTypeAvro.REGISTER;
            case ACTION_LIKE -> ActionTypeAvro.LIKE;
            default -> throw new IllegalArgumentException("Неизвестный тип действия: " + type);
        };
    }

    private static Instant toInstant(Timestamp ts) {
        if (ts.getSeconds() == 0 && ts.getNanos() == 0) {
            return Instant.now();
        }
        return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
    }
}
