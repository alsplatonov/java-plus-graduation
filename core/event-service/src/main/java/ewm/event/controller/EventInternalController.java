package ewm.event.controller;

import ewm.event.dto.EventInternalDto;
import ewm.event.service.EventLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Внутренний API для других микросервисов. Через Gateway не публикуется.
 */
@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
public class EventInternalController {

    private final EventLookupService eventLookupService;

    @GetMapping("/published-count")
    public Map<Long, Long> countPublished(@RequestParam List<Long> initiatorIds) {
        return eventLookupService.countPublishedByInitiators(initiatorIds);
    }

    @GetMapping("/{eventId}")
    public EventInternalDto getEvent(@PathVariable Long eventId) {
        return eventLookupService.getInternalById(eventId);
    }
}
