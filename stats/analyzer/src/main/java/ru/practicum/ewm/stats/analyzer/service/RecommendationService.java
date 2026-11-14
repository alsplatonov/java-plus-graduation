package ru.practicum.ewm.stats.analyzer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.analyzer.model.EventSimilarity;
import ru.practicum.ewm.stats.analyzer.model.UserEventWeight;
import ru.practicum.ewm.stats.analyzer.repository.EventSimilarityRepository;
import ru.practicum.ewm.stats.analyzer.repository.UserEventWeightRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    // Пара (мероприятие, оценка)
    public record Scored(long eventId, double score) {
    }

    private final UserEventWeightRepository weightRepository;
    private final EventSimilarityRepository similarityRepository;

    // Сколько последних взаимодействий пользователя использовать для поиска кандидатов
    @Value("${analyzer.recommendations.recent-interactions:10}")
    private int recentInteractions;

    // Сколько ближайших соседей учитывать при предсказании оценки
    @Value("${analyzer.recommendations.neighbours:5}")
    private int neighbours;

    // Мероприятия, похожие на заданное, с которыми пользователь ещё не взаимодействовал.
    public List<Scored> getSimilarEvents(long eventId, long userId, int maxResults) {
        Set<Long> seen = weightRepository.findByUserId(userId).stream()
                .map(UserEventWeight::getEventId).collect(Collectors.toSet());
        return similarityRepository.findAllByEvent(eventId).stream()
                .filter(s -> !seen.contains(s.otherThan(eventId)))
                .map(s -> new Scored(s.otherThan(eventId), s.getScore()))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(Math.max(maxResults, 0))
                .toList();
    }

    // Рекомендации для пользователя: кандидаты берутся среди мероприятий, похожих на недавно
    // просмотренные, оценка предсказывается как взвешенное среднее оценок ближайших соседей
    public List<Scored> getRecommendationsForUser(long userId, int maxResults) {
        List<UserEventWeight> all = weightRepository.findByUserId(userId);
        if (all.isEmpty() || maxResults <= 0) {
            return List.of();
        }
        Map<Long, Double> ratings = all.stream()
                .collect(Collectors.toMap(UserEventWeight::getEventId, UserEventWeight::getWeight));

        List<UserEventWeight> recent = weightRepository.findByUserIdOrderByUpdatedAtDesc(userId,
                PageRequest.of(0, recentInteractions));

        // кандидаты: непросмотренные мероприятия, похожие на недавние (один запрос на все недавние события)
        Set<Long> recentIds = recent.stream().map(UserEventWeight::getEventId).collect(Collectors.toSet());
        Set<Long> candidateIds = new HashSet<>();
        for (EventSimilarity s : similarityRepository.findAllByEventIn(recentIds)) {
            for (long id : new long[]{s.getEventA(), s.getEventB()}) {
                if (!ratings.containsKey(id)) {
                    candidateIds.add(id);
                }
            }
        }
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        // коэффициенты всех кандидатов загружаются одним запросом и группируются по кандидату
        Map<Long, List<EventSimilarity>> byCandidate = new HashMap<>();
        for (EventSimilarity s : similarityRepository.findAllByEventIn(candidateIds)) {
            if (candidateIds.contains(s.getEventA())) {
                byCandidate.computeIfAbsent(s.getEventA(), k -> new ArrayList<>()).add(s);
            }
            if (candidateIds.contains(s.getEventB())) {
                byCandidate.computeIfAbsent(s.getEventB(), k -> new ArrayList<>()).add(s);
            }
        }

        // сначала оцениваются все кандидаты, затем сортировка по итоговой оценке и только потом limit
        List<Scored> result = new ArrayList<>();
        for (Long candidate : candidateIds) {
            predict(candidate, byCandidate.getOrDefault(candidate, List.of()), ratings).ifPresent(result::add);
        }
        return result.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparingLong(Scored::eventId))
                .limit(maxResults)
                .toList();
    }

    // Сумма максимальных весов всех пользователей по каждому из мероприятий (0 при отсутствии данных)
    public List<Scored> getInteractionsCount(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> totals = new HashMap<>();
        weightRepository.sumWeightsByEventIds(eventIds)
                .forEach(t -> totals.put(t.getEventId(), t.getTotal()));
        Map<Long, Double> ordered = new LinkedHashMap<>();
        eventIds.forEach(id -> ordered.put(id, totals.getOrDefault(id, 0.0)));
        return ordered.entrySet().stream().map(e -> new Scored(e.getKey(), e.getValue())).toList();
    }

    private Optional<Scored> predict(long candidate, List<EventSimilarity> similarities,
                                     Map<Long, Double> ratings) {
        List<EventSimilarity> nearest = similarities.stream()
                .filter(s -> ratings.containsKey(s.otherThan(candidate)))
                .sorted(Comparator.comparingDouble(EventSimilarity::getScore).reversed())
                .limit(neighbours)
                .toList();
        double weighted = 0;
        double sims = 0;
        for (EventSimilarity s : nearest) {
            weighted += s.getScore() * ratings.get(s.otherThan(candidate));
            sims += s.getScore();
        }
        if (sims == 0) {
            return Optional.empty();
        }
        return Optional.of(new Scored(candidate, weighted / sims));
    }
}
