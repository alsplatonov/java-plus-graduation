package ewm.rating.service;

import ewm.rating.dto.AuthorRatingDto;
import ewm.rating.dto.EventRatingDto;
import ewm.rating.entity.RatingType;

import java.util.List;

public interface RatingService {

    void rate(Long userId, Long eventId, RatingType type);

    void delete(Long userId, Long eventId);

    List<EventRatingDto> getRatingsForEvents(List<Long> eventIds);

    List<EventRatingDto> getTopEvents(int from, int size);

    AuthorRatingDto getAuthorRating(Long userId);

    List<AuthorRatingDto> getTopAuthors(int from, int size);
}
