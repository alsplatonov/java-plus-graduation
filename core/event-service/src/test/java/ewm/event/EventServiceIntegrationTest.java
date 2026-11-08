package ewm.event;

import ewm.category.Category;
import ewm.category.CategoryRepository;
import ewm.client.RatingClient;
import ewm.client.RequestClient;
import ewm.client.UserClient;
import ewm.event.dto.EventAdminStateAction;
import ewm.event.dto.EventFullDto;
import ewm.event.dto.EventShortDto;
import ewm.event.dto.LocationDto;
import ewm.event.dto.NewEventDto;
import ewm.event.dto.PublicEventSearchParams;
import ewm.event.dto.PublicEventSort;
import ewm.event.dto.UpdateEventAdminRequest;
import ewm.event.entity.EventState;
import ewm.event.repository.EventRepository;
import ewm.event.service.EventService;
import ewm.exception.ConflictException;
import ewm.rating.dto.EventRatingDto;
import ewm.user.dto.UserDto;
import ewm.user.dto.UserShortDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Межсервисные клиенты подменены заглушками - в тесте поднимается только сервис событий
 */
@SpringBootTest
@Transactional
@Import(EventServiceIntegrationTest.StubClients.class)
class EventServiceIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Long categoryId;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        categoryRepository.deleteAll();

        Category category = new Category();
        category.setName("Концерты");
        categoryId = categoryRepository.save(category).getId();
    }

    @Test
    void shouldCreatePendingEventAndPublishIt() {
        EventFullDto created = eventService.create(USER_ID, newEvent(LocalDateTime.now().plusHours(3)));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getState()).isEqualTo(EventState.PENDING);
        assertThat(created.getCreatedOn()).isNotNull();
        assertThat(created.getInitiator().getName()).isEqualTo("Инициатор");

        UpdateEventAdminRequest request = new UpdateEventAdminRequest();
        request.setStateAction(EventAdminStateAction.PUBLISH_EVENT);
        EventFullDto published = eventService.updateByAdmin(created.getId(), request);

        assertThat(published.getState()).isEqualTo(EventState.PUBLISHED);
        assertThat(published.getPublishedOn()).isNotNull();
        assertThat(eventService.getPublicEvent(created.getId()).getId()).isEqualTo(created.getId());
    }

    @Test
    void shouldRejectEventCreationTooCloseToCurrentTime() {
        assertThatThrownBy(() -> eventService.create(USER_ID, newEvent(LocalDateTime.now().plusMinutes(30))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldSearchPublicEventsAndRespectOffset() {
        EventFullDto first = eventService.create(USER_ID, newEvent(LocalDateTime.now().plusHours(3)));
        NewEventDto secondEvent = newEvent(LocalDateTime.now().plusHours(4));
        secondEvent.setTitle("Второй будущий концерт");
        EventFullDto second = eventService.create(USER_ID, secondEvent);

        publish(first.getId());
        publish(second.getId());

        PublicEventSearchParams searchParams = new PublicEventSearchParams();
        searchParams.setText("концерт");
        searchParams.setCategories(List.of(categoryId));
        searchParams.setPaid(false);
        searchParams.setSort(PublicEventSort.EVENT_DATE);
        searchParams.setFrom(1);
        searchParams.setSize(1);
        List<EventShortDto> events = eventService.getPublicEvents(searchParams);

        assertThat(events).singleElement().extracting(EventShortDto::getId).isEqualTo(second.getId());
    }

    private void publish(Long eventId) {
        UpdateEventAdminRequest request = new UpdateEventAdminRequest();
        request.setStateAction(EventAdminStateAction.PUBLISH_EVENT);
        eventService.updateByAdmin(eventId, request);
    }

    private NewEventDto newEvent(LocalDateTime eventDate) {
        return new NewEventDto(
                "Подробная аннотация будущего концерта",
                categoryId,
                "Подробное описание будущего концерта для всех посетителей",
                eventDate,
                new LocationDto(55.75F, 37.61F),
                false,
                10,
                true,
                "Будущий концерт"
        );
    }

    @TestConfiguration
    static class StubClients {

        @Bean
        @Primary
        UserClient userClient() {
            return new UserClient() {
                @Override
                public UserDto getUser(Long userId) {
                    return new UserDto(userId, "Инициатор", "initiator@example.com");
                }

                @Override
                public List<UserShortDto> getUsers(List<Long> ids) {
                    return ids.stream().map(id -> new UserShortDto(id, "Инициатор")).toList();
                }
            };
        }

        @Bean
        @Primary
        RequestClient requestClient() {
            return new RequestClient() {
                @Override
                public Map<Long, Long> countConfirmed(List<Long> eventIds) {
                    return Map.of();
                }

                @Override
                public boolean hasConfirmedRequest(Long eventId, Long userId) {
                    return false;
                }
            };
        }

        @Bean
        @Primary
        RatingClient ratingClient() {
            return new RatingClient() {
                @Override
                public List<EventRatingDto> getRatings(List<Long> eventIds) {
                    return List.of();
                }

                @Override
                public List<EventRatingDto> getTopEvents(int from, int size) {
                    return List.of();
                }
            };
        }
    }
}
