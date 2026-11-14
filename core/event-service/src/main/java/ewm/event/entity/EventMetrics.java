package ewm.event.entity;

/**
 * Показатели события. Рейтинг рассчитывается сервисом Analyzer на основе действий пользователей
 */
public record EventMetrics(long confirmedRequests,
                           long likes,
                           long dislikes,
                           double rating) {

    public static final EventMetrics EMPTY = new EventMetrics(0, 0, 0, 0.0);
}
