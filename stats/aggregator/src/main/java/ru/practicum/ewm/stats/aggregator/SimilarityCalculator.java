package ru.practicum.ewm.stats.aggregator;

import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Инкрементальный пересчёт сходства мероприятий (косинусное сходство по максимальным весам действий)
 * Хранит в памяти:
 * weights: eventId -> (userId -> максимальный вес действия);
 * userEvents: userId -> события, с которыми взаимодействовал пользователь;
 * eventSums: eventId -> S_A, сумма весов всех пользователей по событию;
 * minSums: сумма минимальных весов пар действий одного пользователя
 * Сходство = S_min / (sqrt(S_A) * sqrt(S_B))
 */
@Component
public class SimilarityCalculator {

    static final double WEIGHT_VIEW = 0.4;
    static final double WEIGHT_REGISTER = 0.8;
    static final double WEIGHT_LIKE = 1.0;

    private final Map<Long, Map<Long, Double>> weights = new HashMap<>();
    private final Map<Long, Set<Long>> userEvents = new HashMap<>();
    private final Map<Long, Double> eventSums = new HashMap<>();
    private final Map<Long, Map<Long, Double>> minSums = new HashMap<>();
    private final Map<Long, TreeSet<Long>> partners = new HashMap<>();

    public static double weightOf(ActionTypeAvro type) {
        return switch (type) {
            case VIEW -> WEIGHT_VIEW;
            case REGISTER -> WEIGHT_REGISTER;
            case LIKE -> WEIGHT_LIKE;
        };
    }

    // Обрабатывает действие и возвращает список обновлённых коэффициентов сходства
    // Если вес действия не превышает уже учтённый, ничего не меняется и возвращается пустой список
    public synchronized List<EventSimilarityAvro> update(UserActionAvro action) {
        long eventId = action.getEventId();
        long userId = action.getUserId();
        double newWeight = weightOf(action.getActionType());

        Map<Long, Double> eventWeights = weights.computeIfAbsent(eventId, k -> new HashMap<>());
        double oldWeight = eventWeights.getOrDefault(userId, 0.0);
        if (newWeight <= oldWeight) {
            return List.of();
        }

        eventWeights.put(userId, newWeight);
        double sumA = eventSums.merge(eventId, newWeight - oldWeight, Double::sum);

        Set<Long> events = userEvents.computeIfAbsent(userId, k -> new HashSet<>());
        events.add(eventId);

        // 1) обновляем S_min для пар события с остальными событиями этого пользователя
        for (Long other : events) {
            if (other == eventId) {
                continue;
            }
            double otherWeight = weights.get(other).get(userId);
            double delta = Math.min(newWeight, otherWeight) - Math.min(oldWeight, otherWeight);

            long first = Math.min(eventId, other);
            long second = Math.max(eventId, other);
            minSums.computeIfAbsent(first, k -> new HashMap<>()).merge(second, delta, Double::sum);
            partners.computeIfAbsent(eventId, k -> new TreeSet<>()).add(other);
            partners.computeIfAbsent(other, k -> new TreeSet<>()).add(eventId);
        }

        // 2) S_A изменилась, поэтому пересчитываем сходство со всеми событиями, у которых есть общая S_min
        //    (в том числе благодаря другим пользователям), в порядке возрастания идентификатора
        List<EventSimilarityAvro> result = new ArrayList<>();
        for (Long other : partners.getOrDefault(eventId, new TreeSet<>())) {
            long first = Math.min(eventId, other);
            long second = Math.max(eventId, other);
            double sMin = minSums.get(first).get(second);
            if (sMin <= 0) {
                continue;
            }
            double score = sMin / (Math.sqrt(sumA) * Math.sqrt(eventSums.get(other)));
            result.add(EventSimilarityAvro.newBuilder()
                    .setEventA(first)
                    .setEventB(second)
                    .setScore(score)
                    .setTimestamp(action.getTimestamp())
                    .build());
        }
        return result;
    }
}
