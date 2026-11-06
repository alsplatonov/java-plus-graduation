package ewm.rating.mapper;

import ewm.rating.dto.AuthorRatingCount;
import ewm.rating.dto.AuthorRatingDto;

public final class RatingMapper {

    private RatingMapper() {
    }

    public static AuthorRatingDto toAuthorRatingDto(Long userId, double rating, long ratedEventsCount) {
        return new AuthorRatingDto(userId, rating, ratedEventsCount);
    }

    public static AuthorRatingDto toAuthorRatingDto(AuthorRatingCount count, long eventsCount) {
        return new AuthorRatingDto(count.getAuthorId(), average(count, eventsCount), eventsCount);
    }

    public static double average(AuthorRatingCount count, long eventsCount) {
        if (eventsCount == 0) {
            return 0.0;
        }
        return Math.round((double) (count.getLikes() - count.getDislikes()) / eventsCount * 100.0) / 100.0;
    }
}
