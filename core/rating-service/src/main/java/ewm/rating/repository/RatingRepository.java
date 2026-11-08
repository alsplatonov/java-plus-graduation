package ewm.rating.repository;

import ewm.rating.dto.AuthorRatingCount;
import ewm.rating.entity.Rating;
import ewm.rating.dto.EventRatingDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findByEventIdAndUserId(Long eventId, Long userId);

    @Query("SELECT new ewm.rating.dto.EventRatingDto("
            + "r.eventId, "
            + "SUM(CASE WHEN r.type = ewm.rating.entity.RatingType.LIKE THEN 1L ELSE 0L END), "
            + "SUM(CASE WHEN r.type = ewm.rating.entity.RatingType.DISLIKE THEN 1L ELSE 0L END)) "
            + "FROM Rating r "
            + "WHERE r.eventId IN :eventIds "
            + "GROUP BY r.eventId")
    List<EventRatingDto> countByEventIds(@Param("eventIds") List<Long> eventIds);

    @Query("SELECT new ewm.rating.dto.EventRatingDto(r.eventId, "
            + "SUM(CASE WHEN r.type = ewm.rating.entity.RatingType.LIKE THEN 1L ELSE 0L END), "
            + "SUM(CASE WHEN r.type = ewm.rating.entity.RatingType.DISLIKE THEN 1L ELSE 0L END)) "
            + "FROM Rating r "
            + "GROUP BY r.eventId "
            + "ORDER BY (SUM(CASE WHEN r.type = ewm.rating.entity.RatingType.LIKE THEN 1L ELSE 0L END) "
            + "- SUM(CASE WHEN r.type = ewm.rating.entity.RatingType.DISLIKE THEN 1L ELSE 0L END)) DESC, "
            + "r.eventId ASC")
    List<EventRatingDto> findTopEvents(Pageable pageable);

    @Query("SELECT new ewm.rating.dto.AuthorRatingCount(r.authorId, "
            + "SUM(CASE WHEN r.type = ewm.rating.entity.RatingType.LIKE THEN 1L ELSE 0L END), "
            + "SUM(CASE WHEN r.type = ewm.rating.entity.RatingType.DISLIKE THEN 1L ELSE 0L END)) "
            + "FROM Rating r "
            + "WHERE r.authorId = :authorId "
            + "GROUP BY r.authorId")
    Optional<AuthorRatingCount> findAuthorRatingCount(@Param("authorId") Long authorId);

    @Query("SELECT new ewm.rating.dto.AuthorRatingCount(r.authorId, "
            + "SUM(CASE WHEN r.type = ewm.rating.entity.RatingType.LIKE THEN 1L ELSE 0L END), "
            + "SUM(CASE WHEN r.type = ewm.rating.entity.RatingType.DISLIKE THEN 1L ELSE 0L END)) "
            + "FROM Rating r "
            + "GROUP BY r.authorId")
    List<AuthorRatingCount> findAllAuthorRatingCounts();
}
