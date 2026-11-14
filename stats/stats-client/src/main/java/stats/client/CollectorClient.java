package stats.client;

import com.google.protobuf.Timestamp;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.grpc.action.ActionTypeProto;
import ru.practicum.ewm.stats.grpc.action.UserActionProto;
import ru.practicum.ewm.stats.grpc.collector.UserActionControllerGrpc;

import java.time.Instant;

/**
 * gRPC-клиент сервиса Collector: отправка действий пользователей (просмотр, регистрация, лайк)
 */
@Slf4j
@Component
public class CollectorClient {

    @GrpcClient("collector")
    private UserActionControllerGrpc.UserActionControllerBlockingStub client;

    public void sendView(long userId, long eventId) {
        send(userId, eventId, ActionTypeProto.ACTION_VIEW);
    }

    public void sendRegister(long userId, long eventId) {
        send(userId, eventId, ActionTypeProto.ACTION_REGISTER);
    }

    public void sendLike(long userId, long eventId) {
        send(userId, eventId, ActionTypeProto.ACTION_LIKE);
    }

    private void send(long userId, long eventId, ActionTypeProto type) {
        Instant now = Instant.now();
        UserActionProto request = UserActionProto.newBuilder()
                .setUserId(userId)
                .setEventId(eventId)
                .setActionType(type)
                .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond())
                        .setNanos(now.getNano())
                        .build())
                .build();
        try {
            client.collectUserAction(request);
        } catch (StatusRuntimeException e) {
            log.warn("Не удалось отправить действие {} пользователя {} над событием {} в Collector: {}",
                    type, userId, eventId, e.getStatus());
        }
    }
}
