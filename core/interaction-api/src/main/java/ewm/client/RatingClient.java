package ewm.client;

import ewm.rating.dto.EventRatingDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "rating-service", contextId = "ratingClient", path = "/internal/ratings",
        configuration = FeignClientConfig.class, primary = false, fallbackFactory = RatingClient.RatingClientFallbackFactory.class)
public interface RatingClient {

    // Лайки и дизлайки нескольких событий одним запросом
    @GetMapping
    List<EventRatingDto> getRatings(@RequestParam("eventIds") List<Long> eventIds);

    // Идентификаторы и счётчики событий с наивысшим рейтингом
    @GetMapping("/top-events")
    List<EventRatingDto> getTopEvents(@RequestParam("from") int from, @RequestParam("size") int size);

    @Slf4j
    @Component
    class RatingClientFallbackFactory implements FallbackFactory<RatingClient> {
        @Override
        public RatingClient create(Throwable cause) {
            FallbackSupport.rethrowBusinessException(cause);
            log.warn("rating-service недоступен: {}", cause.toString());
            return new RatingClient() {
                @Override
                public List<EventRatingDto> getRatings(List<Long> eventIds) {
                    return List.of();
                }

                @Override
                public List<EventRatingDto> getTopEvents(int from, int size) {
                    return List.of();
                }
            };
        }
    }
}
