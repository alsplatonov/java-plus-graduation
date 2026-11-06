package ewm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ewm.exception.ConflictException;
import ewm.exception.NotFoundException;
import ewm.exception.ValidationException;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Настройки Feign-клиентов: преобразование ошибок в доменные исключения и повтор запроса при ответах 5xx.
 */
public class FeignClientConfig {

    private static final long RETRY_PERIOD_MS = 100;
    private static final long RETRY_MAX_PERIOD_MS = 1000;
    private static final int RETRY_MAX_ATTEMPTS = 3;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(RETRY_PERIOD_MS, RETRY_MAX_PERIOD_MS, RETRY_MAX_ATTEMPTS);
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        return (methodKey, response) -> {
            int status = response.status();
            String message = extractMessage(response);
            if (status == 400) {
                return new ValidationException(message);
            }
            if (status == 404) {
                return new NotFoundException(message);
            }
            if (status == 409) {
                return new ConflictException(message);
            }
            if (status >= 500) {
                // RetryableException заставляет Feign повторить запрос (все межсервисные вызовы — GET)
                return new RetryableException(status, "Ошибка " + status + " при вызове " + methodKey,
                        response.request().httpMethod(), (Long) null, response.request());
            }
            return defaultDecoder.decode(methodKey, response);
        };
    }

    private String extractMessage(Response response) {
        if (response.body() == null) {
            return "Ошибка при обращении к сервису, статус " + response.status();
        }
        try (InputStream body = response.body().asInputStream()) {
            JsonNode node = objectMapper.readTree(new String(body.readAllBytes(), StandardCharsets.UTF_8));
            if (node != null && node.hasNonNull("message")) {
                return node.get("message").asText();
            }
        } catch (IOException e) {
            // тело ответа не удалось разобрать — используем сообщение по умолчанию
        }
        return "Ошибка при обращении к сервису, статус " + response.status();
    }
}
