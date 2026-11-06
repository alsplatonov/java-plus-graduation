package ewm.client;

import ewm.exception.ServiceUnavailableException;
import ewm.user.dto.UserDto;
import ewm.user.dto.UserShortDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "user-service", contextId = "userClient", path = "/internal/users",
        configuration = FeignClientConfig.class, primary = false, fallbackFactory = UserClient.UserClientFallbackFactory.class)
public interface UserClient {

    // Пользователь по идентификатору; 404, если не найден
    @GetMapping("/{userId}")
    UserDto getUser(@PathVariable("userId") Long userId);

    // Краткая информация о нескольких пользователях одним запросом
    @GetMapping("/batch")
    List<UserShortDto> getUsers(@RequestParam("ids") List<Long> ids);

    @Slf4j
    @Component
    class UserClientFallbackFactory implements FallbackFactory<UserClient> {
        @Override
        public UserClient create(Throwable cause) {
            FallbackSupport.rethrowBusinessException(cause);
            log.warn("user-service недоступен: {}", cause.toString());
            return new UserClient() {
                @Override
                public UserDto getUser(Long userId) {
                    throw new ServiceUnavailableException("Сервис пользователей временно недоступен.");
                }

                @Override
                public List<UserShortDto> getUsers(List<Long> ids) {
                    return List.of();
                }
            };
        }
    }
}
