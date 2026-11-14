package ru.practicum.ewm.stats.analyzer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Коэффициент сходства пары мероприятий (eventA меньше eventB)
 */
@Entity
@Table(name = "event_similarities")
@IdClass(EventSimilarityId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventSimilarity {

    @Id
    @Column(name = "event_a")
    private Long eventA;

    @Id
    @Column(name = "event_b")
    private Long eventB;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Возвращает идентификатор второго мероприятия пары
    public long otherThan(long eventId) {
        return eventA == eventId ? eventB : eventA;
    }
}
