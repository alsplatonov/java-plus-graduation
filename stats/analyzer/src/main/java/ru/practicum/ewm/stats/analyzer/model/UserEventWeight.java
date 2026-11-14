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
 * Максимальный вес действий пользователя над мероприятием
 */
@Entity
@Table(name = "user_event_weights")
@IdClass(UserEventWeightId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserEventWeight {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "weight", nullable = false)
    private double weight;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
