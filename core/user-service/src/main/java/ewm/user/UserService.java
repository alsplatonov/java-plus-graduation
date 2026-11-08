package ewm.user;

import ewm.user.dto.NewUserRequest;
import ewm.user.dto.UserDto;
import ewm.user.dto.UserSearchParams;
import ewm.user.dto.UserShortDto;

import java.util.List;

public interface UserService {

    UserDto create(NewUserRequest request);

    List<UserDto> getAll(UserSearchParams searchParams);

    User getEntityById(Long userId);

    UserDto getById(Long userId);

    List<UserShortDto> getShortByIds(List<Long> userIds);

    void delete(Long userId);
}
