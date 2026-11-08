package ewm.rating.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Суммарные лайки/дизлайки событий автора (результат запроса в БД rating-service)
 */
@Getter
@AllArgsConstructor
public class AuthorRatingCount {
    private Long authorId;
    private Long likes;
    private Long dislikes;
}
