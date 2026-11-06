package ewm.rating.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventRatingDto {
    private Long eventId;
    private Long likes;
    private Long dislikes;
}
