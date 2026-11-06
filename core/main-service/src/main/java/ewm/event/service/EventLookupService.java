package ewm.event.service;

import ewm.event.dto.EventInternalDto;
import ewm.event.entity.Event;

import java.util.List;
import java.util.Map;

public interface EventLookupService {

    Event getEntityById(Long eventId);

    Event getOwnedEventById(Long userId, Long eventId);

    boolean existsByCategoryId(Long categoryId);

    EventInternalDto getInternalById(Long eventId);

    Map<Long, Long> countPublishedByInitiators(List<Long> initiatorIds);
}