package ewm.request.service;

import ewm.client.EventClient;
import ewm.client.UserClient;
import ewm.event.dto.EventInternalDto;
import ewm.event.entity.EventState;
import ewm.exception.ConflictException;
import ewm.exception.NotFoundException;
import ewm.request.dto.*;
import ewm.request.entity.Request;
import ewm.request.entity.RequestStatus;
import ewm.request.mapper.RequestMapper;
import ewm.request.repository.RequestRepository;
import lombok.RequiredArgsConstructor;
import stats.client.CollectorClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final EventClient eventClient;
    private final UserClient userClient;
    private final PlatformTransactionManager transactionManager;
    private final CollectorClient collectorClient;

    @Override
    public ParticipationRequestDto create(Long userId, Long eventId) {
        // обращения к другим сервисам выполняются вне транзакции БД
        userClient.getUser(userId);
        EventInternalDto event = eventClient.getEvent(eventId);

        ParticipationRequestDto created = inTransaction(() -> createRequest(userId, eventId, event));
        // заявка уже сохранена - фиксируем регистрацию в статистике вне транзакции
        collectorClient.sendRegister(userId, eventId);
        return created;
    }

    private ParticipationRequestDto createRequest(Long userId, Long eventId, EventInternalDto event) {
        if (event.getInitiatorId().equals(userId)) {
            throw new ConflictException("Инициатор события не может добавить запрос на участие в своём событии.");
        }
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Нельзя участвовать в неопубликованном событии.");
        }
        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            throw new ConflictException("Нельзя добавить повторный запрос.");
        }

        int limit = event.getParticipantLimit();
        if (limit > 0) {
            long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
            if (confirmed >= limit) {
                throw new ConflictException("У события достигнут лимит запросов на участие.");
            }
        }

        RequestStatus status = limit == 0 || !event.isRequestModeration()
                ? RequestStatus.CONFIRMED
                : RequestStatus.PENDING;
        Request request = RequestMapper.toEntity(eventId, userId, status);

        return RequestMapper.toDto(requestRepository.save(request));
    }

    @Override
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        userClient.getUser(userId);
        return requestRepository.findAllByRequesterId(userId).stream()
                .map(RequestMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancel(Long userId, Long requestId) {
        Request request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> new NotFoundException("Запрос с идентификатором " + requestId + " не найден."));
        request.setStatus(RequestStatus.CANCELED);
        return RequestMapper.toDto(request);
    }

    @Override
    public List<ParticipationRequestDto> getEventParticipants(Long userId, Long eventId) {
        getOwnedEvent(userId, eventId);
        return requestRepository.findAllByEventId(eventId).stream()
                .map(RequestMapper::toDto)
                .toList();
    }

    @Override
    public EventRequestStatusUpdateResult updateStatus(Long userId, Long eventId,
                                                       EventRequestStatusUpdateRequest updateRequest) {
        // обращение к event-service выполняется вне транзакции БД
        EventInternalDto event = getOwnedEvent(userId, eventId);
        return inTransaction(() -> changeStatuses(eventId, event, updateRequest));
    }

    private EventRequestStatusUpdateResult changeStatuses(Long eventId, EventInternalDto event,
                                                          EventRequestStatusUpdateRequest updateRequest) {
        List<Request> requests = requestRepository.findAllById(updateRequest.getRequestIds());

        if (requests.size() != updateRequest.getRequestIds().size()) {
            throw new NotFoundException("Один или несколько запросов на участие не найдены.");
        }

        for (Request r : requests) {
            if (!r.getEventId().equals(eventId)) {
                throw new NotFoundException("Запрос с идентификатором " + r.getId()
                        + " не относится к событию " + eventId + ".");
            }
            if (r.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Request должен быть в статусе PENDING");
            }
        }

        if (updateRequest.getStatus() == RequestStatusUpdateAction.REJECTED) {
            requests.forEach(r -> r.setStatus(RequestStatus.REJECTED));
            return new EventRequestStatusUpdateResult(List.of(), toDtoList(requests));
        }

        int limit = event.getParticipantLimit();
        long alreadyConfirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (limit > 0 && alreadyConfirmed >= limit) {
            throw new ConflictException("Достигнут лимит участников");
        }

        List<Request> confirmed = new ArrayList<>();
        List<Request> rejected = new ArrayList<>();

        for (Request r : requests) {
            if (limit > 0 && alreadyConfirmed >= limit) {
                r.setStatus(RequestStatus.REJECTED);
                rejected.add(r);
            } else {
                r.setStatus(RequestStatus.CONFIRMED);
                confirmed.add(r);
                alreadyConfirmed++;
            }
        }

        return new EventRequestStatusUpdateResult(toDtoList(confirmed), toDtoList(rejected));
    }

    @Override
    public long countConfirmed(Long eventId) {
        return requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
    }

    @Override
    public Map<Long, Long> countConfirmedForEvents(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }
        return requestRepository.countConfirmedByEventIds(eventIds).stream()
                .collect(Collectors.toMap(EventConfirmedCount::getEventId, EventConfirmedCount::getConfirmedRequests));
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private EventInternalDto getOwnedEvent(Long userId, Long eventId) {
        EventInternalDto event = eventClient.getEvent(eventId);
        if (!event.getInitiatorId().equals(userId)) {
            throw new NotFoundException("Событие с идентификатором " + eventId + " не найдено.");
        }
        return event;
    }

    private List<ParticipationRequestDto> toDtoList(List<Request> requests) {
        return requests.stream().map(RequestMapper::toDto).toList();
    }

    @Override
    public boolean hasConfirmedRequest(Long eventId, Long userId) {
        return requestRepository.existsByEventIdAndRequesterIdAndStatus(eventId, userId, RequestStatus.CONFIRMED);
    }
}