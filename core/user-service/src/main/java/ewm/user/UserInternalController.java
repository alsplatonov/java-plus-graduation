package ewm.user;

import ewm.user.dto.UserDto;
import ewm.user.dto.UserShortDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Внутренний API для других микросервисов. Через Gateway не публикуется
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public UserDto getUser(@PathVariable Long userId) {
        return userService.getById(userId);
    }

    @GetMapping("/batch")
    public List<UserShortDto> getUsers(@RequestParam List<Long> ids) {
        return userService.getShortByIds(ids);
    }
}
