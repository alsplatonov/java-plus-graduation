package ewm.event.controller;

import ewm.event.dto.EventFullDto;
import ewm.event.dto.EventShortDto;
import ewm.event.dto.PublicEventSearchParams;
import ewm.event.service.EventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import stats.client.CollectorClient;

import java.util.List;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Validated
public class EventPublicController {

    private final EventService eventService;
    private final CollectorClient collectorClient;

    @GetMapping
    public List<EventShortDto> getEvents(@Valid @ModelAttribute PublicEventSearchParams searchParams) {
        return eventService.getPublicEvents(searchParams);
    }

    @GetMapping("/{id}")
    public EventFullDto getEvent(@PathVariable Long id,
                                 @RequestHeader("X-EWM-USER-ID") long userId) {
        EventFullDto event = eventService.getPublicEvent(id);
        // просмотр фиксируется только для существующего опубликованного события
        collectorClient.sendView(userId, id);
        return event;
    }

    @GetMapping("/recommendations")
    public List<EventShortDto> getRecommendations(@RequestHeader("X-EWM-USER-ID") long userId,
                                                  @RequestParam(defaultValue = "10") @Positive int maxResults) {
        return eventService.getRecommendations(userId, maxResults);
    }

    @PutMapping("/{eventId}/like")
    public void like(@PathVariable Long eventId,
                     @RequestHeader("X-EWM-USER-ID") long userId) {
        eventService.like(userId, eventId);
    }

    @GetMapping("/top")
    public List<EventShortDto> getTopEvents(@RequestParam(defaultValue = "0") @Min(0) int from,
                                            @RequestParam(defaultValue = "10") @Positive int size) {
        return eventService.getTopEvents(from, size);
    }
}
