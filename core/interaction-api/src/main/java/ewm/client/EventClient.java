package ewm.client;

import ewm.event.dto.EventInternalDto;
import ewm.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "main-service", contextId = "eventClient", path = "/internal/events",
        configuration = FeignClientConfig.class, primary = false, fallbackFactory = EventClient.EventClientFallbackFactory.class)
public interface EventClient {

    // Событие по идентификатору; 404, если не найдено
    @GetMapping("/{eventId}")
    EventInternalDto getEvent(@PathVariable("eventId") Long eventId);

    // Количество опубликованных событий по инициаторам: initiatorId -> count
    @GetMapping("/published-count")
    Map<Long, Long> countPublishedByInitiators(@RequestParam("initiatorIds") List<Long> initiatorIds);

    @Slf4j
    @Component
    class EventClientFallbackFactory implements FallbackFactory<EventClient> {
        @Override
        public EventClient create(Throwable cause) {
            FallbackSupport.rethrowBusinessException(cause);
            log.warn("main-service недоступен: {}", cause.toString());
            return new EventClient() {
                @Override
                public EventInternalDto getEvent(Long eventId) {
                    throw new ServiceUnavailableException("Сервис событий временно недоступен.");
                }

                @Override
                public Map<Long, Long> countPublishedByInitiators(List<Long> initiatorIds) {
                    return Map.of();
                }
            };
        }
    }
}
