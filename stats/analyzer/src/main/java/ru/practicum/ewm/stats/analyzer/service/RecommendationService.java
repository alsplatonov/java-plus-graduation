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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    /** Пара (мероприятие, оценка). */
    public record Scored(long eventId, double score) {
    }

    private final UserEventWeightRepository weightRepository;
    private final EventSimilarityRepository similarityRepository;

    /** Сколько последних взаимодействий пользователя использовать для поиска кандидатов. */
    @Value("${analyzer.recommendations.recent-interactions:10}")
    private int recentInteractions;

    /** Сколько ближайших соседей учитывать при предсказании оценки. */
    @Value("${analyzer.recommendations.neighbours:5}")
    private int neighbours;

    /**
     * Мероприятия, похожие на заданное, с которыми пользователь ещё не взаимодействовал.
     */
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

    /**
     * Рекомендации для пользователя: кандидаты берутся среди мероприятий, похожих на недавно
     * просмотренные, оценка предсказывается как взвешенное среднее оценок ближайших соседей.
     */
    public List<Scored> getRecommendationsForUser(long userId, int maxResults) {
        List<UserEventWeight> all = weightRepository.findByUserId(userId);
        if (all.isEmpty() || maxResults <= 0) {
            return List.of();
        }
        Map<Long, Double> ratings = all.stream()
                .collect(Collectors.toMap(UserEventWeight::getEventId, UserEventWeight::getWeight));

        List<UserEventWeight> recent = weightRepository.findByUserIdOrderByUpdatedAtDesc(userId,
                PageRequest.of(0, recentInteractions));

        // кандидаты: непросмотренные мероприятия, похожие на недавние
        Map<Long, Double> candidates = new HashMap<>();
        for (UserEventWeight w : recent) {
            for (EventSimilarity s : similarityRepository.findAllByEvent(w.getEventId())) {
                long other = s.otherThan(w.getEventId());
                if (!ratings.containsKey(other)) {
                    candidates.merge(other, s.getScore(), Math::max);
                }
            }
        }

        List<Long> candidateIds = candidates.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(Map.Entry::getKey)
                .toList();

        List<Scored> result = new ArrayList<>();
        for (Long candidate : candidateIds) {
            predict(candidate, ratings).ifPresent(result::add);
        }
        result.sort(Comparator.comparingDouble(Scored::score).reversed());
        return result;
    }

    /** Сумма максимальных весов всех пользователей по каждому из мероприятий (0 при отсутствии данных). */
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

    private java.util.Optional<Scored> predict(long candidate, Map<Long, Double> ratings) {
        List<EventSimilarity> nearest = similarityRepository.findAllByEvent(candidate).stream()
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
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Scored(candidate, weighted / sims));
    }
}
