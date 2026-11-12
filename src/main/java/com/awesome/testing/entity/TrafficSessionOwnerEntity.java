package com.awesome.testing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The first authenticated user seen on a traffic session (catalog X-03). Anonymous records of the session,
 * for example a sign-in attempt, are readable only by this user.
 */
@Entity
@Table(name = "traffic_session_owner")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TrafficSessionOwnerEntity {

    @Id
    @Column(name = "client_session_id", length = 128)
    private String clientSessionId;

    @Column(nullable = false, length = 255)
    private String username;

    @Column(name = "bound_at", nullable = false)
    private Instant boundAt;
}
