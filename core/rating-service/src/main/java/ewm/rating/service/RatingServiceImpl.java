package ewm.rating.service;

import ewm.client.EventClient;
import ewm.client.RequestClient;
import ewm.client.UserClient;
import ewm.event.dto.EventInternalDto;
import ewm.event.entity.EventState;
import ewm.exception.ConflictException;
import ewm.exception.NotFoundException;
import ewm.rating.dto.AuthorRatingCount;
import ewm.rating.dto.AuthorRatingDto;
import ewm.rating.dto.EventRatingDto;
import ewm.rating.entity.Rating;
import ewm.rating.entity.RatingType;
import ewm.rating.mapper.RatingMapper;
import ewm.rating.repository.RatingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RatingServiceImpl implements RatingService {

    private final RatingRepository ratingRepository;
    private final EventClient eventClient;
    private final RequestClient requestClient;
    private final UserClient userClient;

    @Override
    @Transactional
    public void rate(Long userId, Long eventId, RatingType type) {
        userClient.getUser(userId);
        EventInternalDto event = eventClient.getEvent(eventId);

        validateCanRate(event, userId);

        ratingRepository.findByEventIdAndUserId(eventId, userId)
                .ifPresentOrElse(
                        rating -> rating.setType(type),
                        () -> {
                            Rating rating = new Rating();
                            rating.setEventId(eventId);
                            rating.setUserId(userId);
                            rating.setAuthorId(event.getInitiatorId());
                            rating.setType(type);
                            rating.setCreated(LocalDateTime.now());
                            ratingRepository.save(rating);
                        });

        log.info("Пользователь {} оценил событие {} как {}", userId, eventId, type);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long eventId) {
        Rating rating = ratingRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Оценка пользователя " + userId + " для события " + eventId + " не найдена."));

        ratingRepository.delete(rating);
        log.info("Пользователь {} удалил оценку события {}", userId, eventId);
    }

    @Override
    public List<EventRatingDto> getRatingsForEvents(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return List.of();
        }
        return ratingRepository.countByEventIds(eventIds);
    }

    @Override
    public List<EventRatingDto> getTopEvents(int from, int size) {
        return ratingRepository.findTopEvents(PageRequest.of(from / size, size));
    }

    @Override
    public AuthorRatingDto getAuthorRating(Long userId) {
        userClient.getUser(userId); // 404, если пользователя нет
        long eventsCount = publishedEventsCount(List.of(userId)).getOrDefault(userId, 0L);
        return ratingRepository.findAuthorRatingCount(userId)
                .map(count -> RatingMapper.toAuthorRatingDto(count, eventsCount))
                .orElseGet(() -> RatingMapper.toAuthorRatingDto(userId, 0.0, eventsCount));
    }

    @Override
    public List<AuthorRatingDto> getTopAuthors(int from, int size) {
        List<AuthorRatingCount> counts = ratingRepository.findAllAuthorRatingCounts();
        if (counts.isEmpty()) {
            return List.of();
        }
        // одно обращение в сервис событий за количеством опубликованных событий всех авторов
        Map<Long, Long> eventsCount = publishedEventsCount(counts.stream().map(AuthorRatingCount::getAuthorId).toList());

        return counts.stream()
                .map(count -> RatingMapper.toAuthorRatingDto(count, eventsCount.getOrDefault(count.getAuthorId(), 0L)))
                .sorted(Comparator.comparing(AuthorRatingDto::getRating).reversed()
                        .thenComparing(AuthorRatingDto::getUserId))
                .skip(from)
                .limit(size)
                .toList();
    }

    private Map<Long, Long> publishedEventsCount(List<Long> authorIds) {
        return eventClient.countPublishedByInitiators(authorIds);
    }

    private void validateCanRate(EventInternalDto event, Long userId) {
        if (event.getInitiatorId().equals(userId)) {
            throw new ConflictException("Нельзя оценивать собственное событие.");
        }
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Оценить можно только опубликованное событие.");
        }
        if (event.getEventDate().isAfter(LocalDateTime.now())) {
            throw new ConflictException("Оценить событие можно только после его окончания.");
        }
        if (!requestClient.hasConfirmedRequest(event.getId(), userId)) {
            throw new ConflictException("Оценить событие может только участник с подтверждённой заявкой.");
        }
    }
}
