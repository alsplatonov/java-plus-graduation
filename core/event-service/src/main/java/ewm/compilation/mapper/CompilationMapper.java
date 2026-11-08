package ewm.compilation.mapper;

import ewm.compilation.dto.CompilationDto;
import ewm.compilation.dto.NewCompilationDto;
import ewm.compilation.entity.Compilation;
import ewm.event.entity.Event;
import ewm.event.mapper.EventMapper;
import ewm.user.dto.UserShortDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CompilationMapper {

    public static Compilation toCompilation(NewCompilationDto compilationDto, Set<Event> events) {
        Compilation compilation = new Compilation();
        compilation.setEvents(events);
        compilation.setPinned(compilationDto.getPinned());
        compilation.setTitle(compilationDto.getTitle());
        return compilation;
    }

    public static CompilationDto toCompilationDto(Compilation compilation, Map<Long, UserShortDto> initiators) {
        return new CompilationDto(
                EventMapper.toEventShortDto(compilation.getEvents(), initiators),
                compilation.getId(),
                compilation.getPinned(),
                compilation.getTitle()
        );
    }
}
