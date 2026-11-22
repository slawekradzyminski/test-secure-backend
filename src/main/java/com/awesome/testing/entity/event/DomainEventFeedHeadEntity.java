package com.awesome.testing.entity.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The single row ({@code id = 1}) whose lock serialises feed position assignment (V29) and which
 * holds the last position given out. See {@code FeedPositions}.
 */
@Entity
@Table(name = "domain_event_feed_head")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DomainEventFeedHeadEntity {

    @Id
    private Integer id;

    @Setter
    @Column(name = "last_position", nullable = false)
    private long lastPosition;
}
