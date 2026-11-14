package stats.client;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.grpc.dashboard.RecommendationsControllerGrpc;
import ru.practicum.ewm.stats.grpc.recommendation.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.grpc.recommendation.RecommendedEventProto;
import ru.practicum.ewm.stats.grpc.recommendation.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.grpc.recommendation.UserPredictionsRequestProto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * gRPC-клиент сервиса Analyzer: рекомендации, похожие мероприятия и рейтинг мероприятий
 * При недоступности Analyzer возвращаются пустой результат / нулевые рейтинги
 */
@Slf4j
@Component
public class AnalyzerClient {

    // Мероприятие и числовая оценка (рейтинг или сходство)
    public record EventScore(long eventId, double score) {
    }

    @GrpcClient("analyzer")
    private RecommendationsControllerGrpc.RecommendationsControllerBlockingStub client;

    public List<EventScore> getRecommendationsForUser(long userId, int maxResults) {
        UserPredictionsRequestProto request = UserPredictionsRequestProto.newBuilder()
                .setUserId(userId)
                .setMaxResults(maxResults)
                .build();
        return collect(() -> client.getRecommendationsForUser(request), "рекомендации пользователя " + userId);
    }

    public List<EventScore> getSimilarEvents(long eventId, long userId, int maxResults) {
        SimilarEventsRequestProto request = SimilarEventsRequestProto.newBuilder()
                .setEventId(eventId)
                .setUserId(userId)
                .setMaxResults(maxResults)
                .build();
        return collect(() -> client.getSimilarEvents(request), "похожие на " + eventId + " события");
    }

    // Рейтинг (сумма максимальных весов действий всех пользователей) по каждому мероприятию
    public Map<Long, Double> getInteractionsCount(List<Long> eventIds) {
        Map<Long, Double> result = new HashMap<>();
        if (eventIds.isEmpty()) {
            return result;
        }
        InteractionsCountRequestProto request = InteractionsCountRequestProto.newBuilder()
                .addAllEventId(eventIds)
                .build();
        collect(() -> client.getInteractionsCount(request), "рейтинг событий")
                .forEach(item -> result.put(item.eventId(), item.score()));
        return result;
    }

    private List<EventScore> collect(java.util.function.Supplier<Iterator<RecommendedEventProto>> call,
                                     String description) {
        List<EventScore> result = new ArrayList<>();
        try {
            Iterator<RecommendedEventProto> it = call.get();
            while (it.hasNext()) {
                RecommendedEventProto item = it.next();
                result.add(new EventScore(item.getEventId(), item.getScore()));
            }
        } catch (StatusRuntimeException e) {
            log.warn("Analyzer недоступен, не удалось получить {}: {}", description, e.getStatus());
            return new ArrayList<>();
        }
        return result;
    }
}
