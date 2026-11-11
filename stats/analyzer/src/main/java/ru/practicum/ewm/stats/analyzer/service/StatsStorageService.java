package ru.practicum.ewm.stats.analyzer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.analyzer.model.EventSimilarity;
import ru.practicum.ewm.stats.analyzer.model.EventSimilarityId;
import ru.practicum.ewm.stats.analyzer.model.UserEventWeight;
import ru.practicum.ewm.stats.analyzer.model.UserEventWeightId;
import ru.practicum.ewm.stats.analyzer.repository.EventSimilarityRepository;
import ru.practicum.ewm.stats.analyzer.repository.UserEventWeightRepository;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Instant;

/**
 * Сохранение данных, пришедших из Kafka.
 */
@Service
@RequiredArgsConstructor
public class StatsStorageService {

    private final UserEventWeightRepository weightRepository;
    private final EventSimilarityRepository similarityRepository;

    /** Сохраняет максимальный вес действия пользователя над мероприятием. */
    @Transactional
    public void saveUserAction(UserActionAvro action) {
        double weight = weightOf(action);
        Instant time = action.getTimestamp();
        UserEventWeight entity = weightRepository
                .findById(new UserEventWeightId(action.getUserId(), action.getEventId()))
                .orElse(null);
        if (entity == null) {
            weightRepository.save(new UserEventWeight(action.getUserId(), action.getEventId(), weight, time));
            return;
        }
        if (weight > entity.getWeight()) {
            entity.setWeight(weight);
        }
        if (time.isAfter(entity.getUpdatedAt())) {
            entity.setUpdatedAt(time);
        }
    }

    /** Сохраняет (или обновляет) коэффициент сходства пары мероприятий. */
    @Transactional
    public void saveSimilarity(EventSimilarityAvro similarity) {
        long first = Math.min(similarity.getEventA(), similarity.getEventB());
        long second = Math.max(similarity.getEventA(), similarity.getEventB());
        EventSimilarity entity = similarityRepository.findById(new EventSimilarityId(first, second)).orElse(null);
        if (entity == null) {
            similarityRepository.save(new EventSimilarity(first, second, similarity.getScore(),
                    similarity.getTimestamp()));
        } else {
            entity.setScore(similarity.getScore());
            entity.setUpdatedAt(similarity.getTimestamp());
        }
    }

    private static double weightOf(UserActionAvro action) {
        return switch (action.getActionType()) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
        };
    }
}
