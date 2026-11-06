package ewm.rating.controller;

import ewm.rating.dto.EventRatingDto;
import ewm.rating.service.RatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Внутренний API для других микросервисов. Через Gateway не публикуется
 */
@RestController
@RequestMapping("/internal/ratings")
@RequiredArgsConstructor
public class RatingInternalController {

    private final RatingService ratingService;

    @GetMapping
    public List<EventRatingDto> getRatings(@RequestParam List<Long> eventIds) {
        return ratingService.getRatingsForEvents(eventIds);
    }

    @GetMapping("/top-events")
    public List<EventRatingDto> getTopEvents(@RequestParam(defaultValue = "0") int from,
                                             @RequestParam(defaultValue = "10") int size) {
        return ratingService.getTopEvents(from, size);
    }
}
