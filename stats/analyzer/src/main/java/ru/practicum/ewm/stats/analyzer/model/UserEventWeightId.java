package ru.practicum.ewm.stats.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserEventWeightId implements Serializable {
    private Long userId;
    private Long eventId;
}
