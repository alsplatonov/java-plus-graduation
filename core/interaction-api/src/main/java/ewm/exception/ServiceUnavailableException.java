package ewm.exception;

/**
 * Зависимый микросервис недоступен, а безопасного значения по умолчанию для операции нет
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
