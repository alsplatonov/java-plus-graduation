package ewm.event.dto;

import ewm.event.entity.EventState;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Минимальное представление события для внутренних запросов других микросервисов
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventInternalDto {
    private Long id;
    private Long initiatorId;
    private EventState state;
    private int participantLimit;
    private boolean requestModeration;
    private LocalDateTime eventDate;
}
