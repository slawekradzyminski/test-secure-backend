package com.awesome.testing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mfa_credential")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaCredentialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(name = "secret_ciphertext", nullable = false, length = 2048)
    private String secretCiphertext;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "last_accepted_time_step")
    private Long lastAcceptedTimeStep;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "setup_expires_at", nullable = false)
    private Instant setupExpiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    private long version;
}
