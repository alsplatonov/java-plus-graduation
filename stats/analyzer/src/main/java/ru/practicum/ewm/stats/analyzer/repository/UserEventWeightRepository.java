package ru.practicum.ewm.stats.analyzer.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.stats.analyzer.model.UserEventWeight;
import ru.practicum.ewm.stats.analyzer.model.UserEventWeightId;

import java.util.Collection;
import java.util.List;

public interface UserEventWeightRepository extends JpaRepository<UserEventWeight, UserEventWeightId> {

    List<UserEventWeight> findByUserId(Long userId);

    List<UserEventWeight> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    @Query("select w.eventId as eventId, sum(w.weight) as total from UserEventWeight w "
            + "where w.eventId in :eventIds group by w.eventId")
    List<EventTotal> sumWeightsByEventIds(@Param("eventIds") Collection<Long> eventIds);

    interface EventTotal {
        Long getEventId();

        Double getTotal();
    }
}
