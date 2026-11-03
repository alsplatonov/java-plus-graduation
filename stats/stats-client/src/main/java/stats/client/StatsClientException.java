package stats.client;

 // Сервис статистики недоступен: не найден в службе обнаружения или вернул ошибку
public class StatsClientException extends RuntimeException {

    public StatsClientException(String message) {
        super(message);
    }

    public StatsClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
