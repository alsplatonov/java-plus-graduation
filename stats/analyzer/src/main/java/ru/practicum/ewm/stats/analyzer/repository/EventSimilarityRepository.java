package ru.practicum.ewm.stats.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.stats.analyzer.model.EventSimilarity;
import ru.practicum.ewm.stats.analyzer.model.EventSimilarityId;

import java.util.Collection;
import java.util.List;

public interface EventSimilarityRepository extends JpaRepository<EventSimilarity, EventSimilarityId> {

    @Query("select s from EventSimilarity s where s.eventA = :eventId or s.eventB = :eventId")
    List<EventSimilarity> findAllByEvent(@Param("eventId") Long eventId);

    // Коэффициенты сходства, в которых участвует хотя бы одно из мероприятий (один запрос на весь набор)
    @Query("select s from EventSimilarity s where s.eventA in :ids or s.eventB in :ids")
    List<EventSimilarity> findAllByEventIn(@Param("ids") Collection<Long> ids);
}
