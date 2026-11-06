package ewm.event.service;

import ewm.event.dto.EventInternalDto;
import ewm.event.entity.Event;
import ewm.event.repository.EventRepository;
import ewm.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventLookupServiceImpl implements EventLookupService {

    private final EventRepository eventRepository;

    @Override
    public Event getEntityById(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с идентификатором " + eventId + " не найдено."));
    }

    @Override
    public Event getOwnedEventById(Long userId, Long eventId) {
        return eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с идентификатором " + eventId + " не найдено."));
    }

    @Override
    public boolean existsByCategoryId(Long categoryId) {
        return eventRepository.existsByCategoryId(categoryId);
    }

    @Override
    public EventInternalDto getInternalById(Long eventId) {
        Event event = getEntityById(eventId);
        return new EventInternalDto(event.getId(), event.getInitiatorId(), event.getState(),
                event.getParticipantLimit(), event.isRequestModeration(), event.getEventDate());
    }

    @Override
    public Map<Long, Long> countPublishedByInitiators(List<Long> initiatorIds) {
        if (initiatorIds == null || initiatorIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : eventRepository.countPublishedByInitiatorIds(initiatorIds)) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
    }
}
