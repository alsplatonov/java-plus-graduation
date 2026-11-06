package ewm.client;

import ewm.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "request-service", contextId = "requestClient", path = "/internal/requests",
        configuration = FeignClientConfig.class, primary = false, fallbackFactory = RequestClient.RequestClientFallbackFactory.class)
public interface RequestClient {

    // Количество подтверждённых заявок по событиям одним запросом: eventId -> count
    @GetMapping("/confirmed-count")
    Map<Long, Long> countConfirmed(@RequestParam("eventIds") List<Long> eventIds);

    // Есть ли у пользователя подтверждённая заявка на событие
    @GetMapping("/confirmed")
    boolean hasConfirmedRequest(@RequestParam("eventId") Long eventId, @RequestParam("userId") Long userId);

    @Slf4j
    @Component
    class RequestClientFallbackFactory implements FallbackFactory<RequestClient> {
        @Override
        public RequestClient create(Throwable cause) {
            FallbackSupport.rethrowBusinessException(cause);
            log.warn("request-service недоступен: {}", cause.toString());
            return new RequestClient() {
                @Override
                public Map<Long, Long> countConfirmed(List<Long> eventIds) {
                    return Map.of();
                }

                @Override
                public boolean hasConfirmedRequest(Long eventId, Long userId) {
                    throw new ServiceUnavailableException("Сервис заявок временно недоступен.");
                }
            };
        }
    }
}
