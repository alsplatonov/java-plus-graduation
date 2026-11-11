package ewm.event.service;

import ewm.category.Category;
import ewm.category.CategoryService;
import ewm.client.RatingClient;
import ewm.client.RequestClient;
import ewm.client.UserClient;
import ewm.common.dto.OffsetPageRequest;
import ewm.common.dto.PageRequestDto;
import ewm.event.dto.*;
import ewm.event.entity.*;
import ewm.event.mapper.EventMapper;
import ewm.event.repository.EventKeyFinder;
import ewm.event.repository.EventRepository;
import ewm.exception.ConflictException;
import ewm.exception.NotFoundException;
import ewm.exception.ValidationException;
import ewm.rating.dto.EventRatingDto;
import ewm.user.dto.UserDto;
import ewm.user.dto.UserShortDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import stats.client.AnalyzerClient;
import stats.client.CollectorClient;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private static final int REMOTE_BATCH_SIZE = 200;
    private static final int EVENT_LEAD_TIME_HOURS = 2;
    private static final int ADMIN_EVENT_LEAD_TIME_HOURS = 1;

    private final EventRepository eventRepository;
    private final EventKeyFinder eventKeyFinder;
    private final UserClient userClient;
    private final CategoryService categoryService;
    private final RequestClient requestClient;
    private final RatingClient ratingClient;
    private final InitiatorResolver initiatorResolver;
    private final AnalyzerClient analyzerClient;
    private final CollectorClient collectorClient;

    @Override
    public List<EventShortDto> getUserEvents(Long userId, PageRequestDto pageRequest) {
        getUser(userId);
        List<Event> events = getPage(new EventPage(pageRequest, Sort.by("id")),
                pageable -> eventRepository.findAllByInitiatorId(userId, pageable));
        return toShortDtos(events, metricsFor(eventIdsOf(events)));
    }

    @Override
    @Transactional
    public EventFullDto create(Long userId, NewEventDto dto) {
        validateEventDate(dto.getEventDate());
        UserDto initiator = getUser(userId);
        Category category = getCategory(dto.getCategory());
        Event event = EventMapper.toEvent(dto, new EventCreationContext(category, initiator.getId()));
        Event saved = eventRepository.save(event);
        return EventMapper.toEventFullDto(saved, metricsFor(saved.getId()),
                new UserShortDto(initiator.getId(), initiator.getName()));
    }

    @Override
    public EventFullDto getUserEvent(Long userId, Long eventId) {
        Event event = getUserEventOrThrow(userId, eventId);
        return toFullDto(event);
    }

    @Override
    @Transactional
    public EventFullDto updateByUser(UserEventPath eventPath, UpdateEventUserRequest request) {
        Event event = getUserEventOrThrow(eventPath.getUserId(), eventPath.getEventId());
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Изменять можно только события в состоянии ожидания модерации или отменённые.");
        }
        if (request.getEventDate() != null) {
            validateEventDate(request.getEventDate());
            event.setEventDate(request.getEventDate());
        }
        if (request.getAnnotation() != null) {
            event.setAnnotation(request.getAnnotation());
        }
        if (request.getCategory() != null) {
            event.setCategory(getCategory(request.getCategory()));
        }
        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }
        if (request.getLocation() != null) {
            event.setLocation(EventMapper.toLocation(request.getLocation()));
        }
        if (request.getPaid() != null) {
            event.setPaid(request.getPaid());
        }
        if (request.getParticipantLimit() != null) {
            event.setParticipantLimit(request.getParticipantLimit());
        }
        if (request.getRequestModeration() != null) {
            event.setRequestModeration(request.getRequestModeration());
        }
        if (request.getStateAction() != null) {
            event.setState(toState(request.getStateAction()));
        }
        if (request.getTitle() != null) {
            event.setTitle(request.getTitle());
        }
        return toFullDto(event);
    }

    @Override
    public List<EventFullDto> getAdminEvents(AdminEventSearchParams searchParams) {
        List<Event> events = getPage(new EventPage(searchParams, Sort.by("id")), pageable -> eventRepository.findAll(
                EventSpecification.byAdminFilters(searchParams), pageable));
        Map<Long, EventMetrics> metrics = metricsFor(eventIdsOf(events));
        Map<Long, UserShortDto> initiators = initiatorsOf(events);
        return events.stream()
                .map(event -> EventMapper.toEventFullDto(event, metrics.get(event.getId()),
                        initiatorResolver.pick(initiators, event.getInitiatorId())))
                .toList();
    }

    @Override
    @Transactional
    public EventFullDto updateByAdmin(Long eventId, UpdateEventAdminRequest request) {
        Event event = getEvent(eventId);
        if (request.getEventDate() != null) {
            validateAdminEventDate(request.getEventDate());
            event.setEventDate(request.getEventDate());
        }
        if (request.getAnnotation() != null) {
            event.setAnnotation(request.getAnnotation());
        }
        if (request.getCategory() != null) {
            event.setCategory(getCategory(request.getCategory()));
        }
        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }
        if (request.getLocation() != null) {
            event.setLocation(EventMapper.toLocation(request.getLocation()));
        }
        if (request.getPaid() != null) {
            event.setPaid(request.getPaid());
        }
        if (request.getParticipantLimit() != null) {
            event.setParticipantLimit(request.getParticipantLimit());
        }
        if (request.getRequestModeration() != null) {
            event.setRequestModeration(request.getRequestModeration());
        }
        if (request.getStateAction() != null) {
            updateAdminState(event, request.getStateAction());
        }
        if (request.getTitle() != null) {
            event.setTitle(request.getTitle());
        }
        return toFullDto(event);
    }

    @Override
    public List<EventShortDto> getPublicEvents(PublicEventSearchParams searchParams) {
        validateRange(searchParams.getRangeStart(), searchParams.getRangeEnd());

        if (searchParams.getRangeStart() == null && searchParams.getRangeEnd() == null) {
            searchParams.setRangeStart(LocalDateTime.now());
        }

        Specification<Event> specification = EventSpecification.byPublicFilters(searchParams);
        PublicEventSort sort = searchParams.getSort();
        boolean sortedByMetric = sortedByMetric(sort);

        if (!sortedByMetric && !searchParams.isOnlyAvailable()) {
            // порядок и страница определяются целиком на уровне БД
            List<Event> events = getPage(new EventPage(searchParams, Sort.by("eventDate", "id")),
                    pageable -> eventRepository.findAll(specification, pageable));
            return toShortDtos(events, metricsFor(eventIdsOf(events)));
        }
        return getRankedPublicEvents(searchParams, specification, sort);
    }

    private static boolean sortedByMetric(PublicEventSort sort) {
        return sort == PublicEventSort.VIEWS || sort == PublicEventSort.RATING;
    }

    private List<EventShortDto> getRankedPublicEvents(PublicEventSearchParams searchParams,
                                                      Specification<Event> specification, PublicEventSort sort) {
        List<Object[]> keys = eventKeyFinder.findKeys(specification);
        if (keys.isEmpty()) {
            return List.of();
        }
        List<Long> allIds = keys.stream().map(key -> (Long) key[0]).toList();
        Map<Long, EventMetrics> metrics = metricsFor(allIds);

        Stream<Object[]> candidates = keys.stream();
        if (searchParams.isOnlyAvailable()) {
            candidates = candidates.filter(key -> {
                int limit = ((Number) key[1]).intValue();
                return limit == 0
                        || metrics.getOrDefault((Long) key[0], EventMetrics.EMPTY).confirmedRequests() < limit;
            });
        }
        List<Long> ids = candidates.map(key -> (Long) key[0]).collect(Collectors.toList());

        if (sortedByMetric(sort)) {
            // просмотры теперь учитываются в рейтинге, поэтому обе сортировки используют рейтинг из Analyzer
            ids.sort(Comparator.comparingDouble((Long id) -> metrics.get(id).rating()).reversed());
        }

        List<Long> pageIds = ids.stream()
                .skip(searchParams.getFrom())
                .limit(searchParams.getSize())
                .toList();
        if (pageIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Event> eventById = eventRepository.findAllById(pageIds).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        List<Event> page = pageIds.stream().map(eventById::get).filter(Objects::nonNull).toList();
        return toShortDtos(page, metrics);
    }

    @Override
    public EventFullDto getPublicEvent(Long eventId) {
        Event event = getEvent(eventId);
        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Событие с идентификатором " + eventId + " не найдено.");
        }
        return toFullDto(event);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<EventShortDto> getRecommendations(long userId, int maxResults) {
        List<Long> ids = analyzerClient.getRecommendationsForUser(userId, maxResults).stream()
                .map(AnalyzerClient.EventScore::eventId)
                .toList();
        return toOrderedPublishedShortDtos(ids);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void like(long userId, long eventId) {
        Event event = getEvent(eventId);
        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Событие с идентификатором " + eventId + " не найдено.");
        }
        if (!requestClient.hasConfirmedRequest(eventId, userId)) {
            throw new ValidationException("Пользователь " + userId
                    + " не посещал мероприятие " + eventId + ", поставить лайк нельзя.");
        }
        collectorClient.sendLike(userId, eventId);
    }

    private List<EventShortDto> toOrderedPublishedShortDtos(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Event> eventById = eventRepository.findAllById(ids).stream()
                .filter(event -> event.getState() == EventState.PUBLISHED)
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        List<Long> existing = ids.stream().filter(eventById::containsKey).toList();
        Map<Long, EventMetrics> metrics = metricsFor(existing);
        Map<Long, UserShortDto> initiators = initiatorsOf(eventById.values());
        return existing.stream()
                .map(eventById::get)
                .map(event -> EventMapper.toEventShortDto(event, metrics.get(event.getId()),
                        initiatorResolver.pick(initiators, event.getInitiatorId())))
                .toList();
    }

    @Override
    public Set<Event> getEventsByIds(Set<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return Set.of();

        List<Event> foundEvents = eventRepository.findAllById(eventIds);

        if (foundEvents.size() != eventIds.size()) {
            throw new NotFoundException("Один или несколько запрошенных событий не найдены в базе данных");
        }

        return new HashSet<>(foundEvents);
    }

    @Override
    public List<EventShortDto> getTopEvents(int from, int size) {
        List<Long> topEventIds = ratingClient.getTopEvents(from, size).stream()
                .map(EventRatingDto::getEventId)
                .toList();

        if (topEventIds.isEmpty()) {
            return List.of();
        }

        // события, которых нет в БД (например, удалены), пропускаем
        Map<Long, Event> eventById = eventRepository.findAllById(topEventIds).stream()
                .collect(Collectors.toMap(Event::getId, event -> event));
        Map<Long, EventMetrics> metrics = metricsFor(topEventIds);
        Map<Long, UserShortDto> initiators = initiatorsOf(eventById.values());

        return topEventIds.stream()
                .map(eventById::get)
                .filter(Objects::nonNull)
                .map(event -> EventMapper.toEventShortDto(event, metrics.get(event.getId()),
                        initiatorResolver.pick(initiators, event.getInitiatorId())))
                .toList();
    }

    private EventState toState(EventUserStateAction stateAction) {
        return switch (stateAction) {
            case SEND_TO_REVIEW -> EventState.PENDING;
            case CANCEL_REVIEW -> EventState.CANCELED;
        };
    }

    private void validateEventDate(LocalDateTime eventDate) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(EVENT_LEAD_TIME_HOURS))) {
            throw new ConflictException("Дата события должна быть не ранее чем через два часа от текущего момента.");
        }
    }

    private void validateAdminEventDate(LocalDateTime eventDate) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(ADMIN_EVENT_LEAD_TIME_HOURS))) {
            throw new ConflictException("Дата события должна быть не ранее чем через один час от текущего момента.");
        }
    }

    private List<Event> getPage(EventPage page, Function<Pageable, Page<Event>> loader) {

        return loader.apply(
                OffsetPageRequest.of(page.from, page.size, page.sort)
        ).getContent();
    }

    private void validateRange(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Дата rangeStart не может быть позже даты rangeEnd.");
        }
    }

    private void updateAdminState(Event event, EventAdminStateAction stateAction) {
        if (stateAction == EventAdminStateAction.PUBLISH_EVENT) {
            if (event.getState() != EventState.PENDING) {
                throw new ConflictException("Нельзя опубликовать событие: оно должно находиться в состоянии ожидания "
                        + "модерации. Текущее состояние: " + event.getState() + ".");
            }
            event.setState(EventState.PUBLISHED);
            event.setPublishedOn(LocalDateTime.now());
            return;
        }
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Нельзя отменить уже опубликованное событие.");
        }
        event.setState(EventState.CANCELED);
    }

    private UserDto getUser(Long userId) {
        return userClient.getUser(userId);
    }

    private Map<Long, UserShortDto> initiatorsOf(Collection<Event> events) {
        return initiatorResolver.resolve(events.stream().map(Event::getInitiatorId).toList());
    }

    private EventFullDto toFullDto(Event event) {
        return EventMapper.toEventFullDto(event, metricsFor(event.getId()),
                initiatorResolver.pick(initiatorsOf(List.of(event)), event.getInitiatorId()));
    }

    private List<EventShortDto> toShortDtos(List<Event> events, Map<Long, EventMetrics> metrics) {
        Map<Long, UserShortDto> initiators = initiatorsOf(events);
        return events.stream()
                .map(event -> EventMapper.toEventShortDto(event, metrics.get(event.getId()),
                        initiatorResolver.pick(initiators, event.getInitiatorId())))
                .toList();
    }

    private Category getCategory(Long categoryId) {
        return categoryService.getEntityById(categoryId);
    }

    private Event getUserEventOrThrow(Long userId, Long eventId) {
        return eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с идентификатором " + eventId + " не найдено."));
    }

    private Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с идентификатором " + eventId + " не найдено."));
    }

    private List<Long> eventIdsOf(List<Event> events) {
        return events.stream().map(Event::getId).toList();
    }

    private EventMetrics metricsFor(Long eventId) {
        Map<Long, EventMetrics> metrics = metricsFor(List.of(eventId));
        return metrics.get(eventId);
    }

    private Map<Long, EventMetrics> metricsFor(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        // по одному запросу в каждый сервис на всю выборку событий; при недоступности сервиса — значения 0
        Map<Long, Long> confirmedByEvent = new HashMap<>();
        Map<Long, EventRatingDto> ratingsByEvent = new HashMap<>();
        for (List<Long> chunk : partition(eventIds)) {
            confirmedByEvent.putAll(requestClient.countConfirmed(chunk));
            ratingClient.getRatings(chunk).forEach(rating -> ratingsByEvent.putIfAbsent(rating.getEventId(), rating));
        }
        Map<Long, Double> ratingByEvent = analyzerClient.getInteractionsCount(eventIds);

        return eventIds.stream().collect(Collectors.toMap(
                id -> id,
                id -> {
                    EventRatingDto rating = ratingsByEvent.get(id);

                    long likes = rating != null ? rating.getLikes() : 0L;
                    long dislikes = rating != null ? rating.getDislikes() : 0L;

                    return new EventMetrics(
                            confirmedByEvent.getOrDefault(id, 0L),
                            likes,
                            dislikes,
                            ratingByEvent.getOrDefault(id, 0.0));
                }
        ));
    }

    private static final class EventPage {

        private final int from;
        private final int size;
        private final Sort sort;

        private EventPage(PageRequestDto pageRequest, Sort sort) {
            this.from = pageRequest.getFrom();
            this.size = pageRequest.getSize();
            this.sort = sort;
        }
    }

    // Разбивает идентификаторы на части, чтобы не превышать допустимую длину URL при вызовах других сервисов
    private static List<List<Long>> partition(List<Long> ids) {
        List<List<Long>> chunks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += REMOTE_BATCH_SIZE) {
            chunks.add(ids.subList(i, Math.min(i + REMOTE_BATCH_SIZE, ids.size())));
        }
        return chunks;
    }

}