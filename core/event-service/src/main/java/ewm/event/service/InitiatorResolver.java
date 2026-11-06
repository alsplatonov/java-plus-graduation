package ewm.event.service;

import ewm.client.UserClient;
import ewm.user.dto.UserShortDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Получает краткую информацию об инициаторах событий из user-service одним запросом на всю выборку.
 * Если user-service недоступен, fallback возвращает пустой результат, и инициатор отдаётся без имени.
 */
@Component
@RequiredArgsConstructor
public class InitiatorResolver {

    private final UserClient userClient;

    public Map<Long, UserShortDto> resolve(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userClient.getUsers(userIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(UserShortDto::getId, Function.identity(), (first, second) -> first));
    }

    public UserShortDto pick(Map<Long, UserShortDto> initiators, Long userId) {
        return initiators.getOrDefault(userId, new UserShortDto(userId, null));
    }
}
