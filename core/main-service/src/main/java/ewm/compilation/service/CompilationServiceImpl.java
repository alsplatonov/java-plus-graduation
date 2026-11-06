package ewm.compilation.service;

import ewm.compilation.dto.CompilationDto;
import ewm.compilation.dto.CompilationSearchParams;
import ewm.compilation.dto.NewCompilationDto;
import ewm.compilation.dto.UpdateCompilationRequest;
import ewm.compilation.entity.Compilation;
import ewm.compilation.mapper.CompilationMapper;
import ewm.compilation.repository.CompilationRepository;
import ewm.event.entity.Event;
import ewm.event.service.EventService;
import ewm.event.service.InitiatorResolver;
import ewm.exception.NotFoundException;
import ewm.user.dto.UserShortDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationServiceImpl implements CompilationService {
    private final CompilationRepository compilationRepository;
    private final EventService eventService;
    private final InitiatorResolver initiatorResolver;

    @Override
    @Transactional
    public CompilationDto create(NewCompilationDto newCompilation) {
        Set<Event> events = eventService.getEventsByIds(newCompilation.getEvents());
        Compilation compilation = CompilationMapper.toCompilation(newCompilation, events);
        CompilationDto createdCompilation = toDto(compilationRepository.save(compilation));
        log.info("Создана подборка событий {}", createdCompilation);
        return createdCompilation;
    }

    @Override
    @Transactional
    public CompilationDto update(UpdateCompilationRequest newCompilation, Long compId) {
        Compilation compilation = findById(compId);

        if (newCompilation.getPinned() != null) compilation.setPinned(newCompilation.getPinned());
        if (newCompilation.getTitle() != null) compilation.setTitle(newCompilation.getTitle());
        if (newCompilation.getEvents() != null) {
            Set<Event> updatedEvents = eventService.getEventsByIds(newCompilation.getEvents());
            compilation.setEvents(updatedEvents);
        }

        CompilationDto updatedCompilation = toDto(compilationRepository.save(compilation));
        log.info("Подборка событий обновлена {}", updatedCompilation);
        return updatedCompilation;
    }

    @Override
    public CompilationDto getCompilationById(Long compId) {
        return toDto(findById(compId));
    }

    @Override
    public List<CompilationDto> getCompilationsByParams(CompilationSearchParams params) {
        Pageable pageable = PageRequest.of(params.getFrom() / params.getSize(), params.getSize());
        List<Compilation> compilations = compilationRepository.findAll(params.getPinned(), pageable);
        // инициаторы всех событий всех подборок запрашиваются у user-service одним обращением
        Map<Long, UserShortDto> initiators = initiatorResolver.resolve(compilations.stream()
                .flatMap(compilation -> compilation.getEvents().stream())
                .map(Event::getInitiatorId)
                .toList());
        return compilations.stream()
                .map(compilation -> CompilationMapper.toCompilationDto(compilation, initiators))
                .toList();
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        getCompilationById(compId);
        compilationRepository.deleteById(compId);
        log.info("Удалёна подборка событий с id {}", compId);
    }

    private CompilationDto toDto(Compilation compilation) {
        Collection<Long> initiatorIds = compilation.getEvents().stream().map(Event::getInitiatorId).toList();
        return CompilationMapper.toCompilationDto(compilation, initiatorResolver.resolve(initiatorIds));
    }

    private Compilation findById(Long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка событий с идентификатором " + compId + " не найдена."));
    }
}
