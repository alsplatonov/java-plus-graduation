package ewm.request.controller;

import ewm.request.service.RequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Внутренний API для других микросервисов. Через Gateway не публикуется
 */
@RestController
@RequestMapping("/internal/requests")
@RequiredArgsConstructor
public class RequestInternalController {

    private final RequestService requestService;

    @GetMapping("/confirmed-count")
    public Map<Long, Long> countConfirmed(@RequestParam List<Long> eventIds) {
        return requestService.countConfirmedForEvents(eventIds);
    }

    @GetMapping("/confirmed")
    public boolean hasConfirmedRequest(@RequestParam Long eventId, @RequestParam Long userId) {
        return requestService.hasConfirmedRequest(eventId, userId);
    }
}
