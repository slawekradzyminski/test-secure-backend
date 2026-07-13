package com.awesome.testing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mfa_recovery_code", uniqueConstraints = @UniqueConstraint(
        name = "uk_mfa_recovery_code_credential_selector",
        columnNames = {"credential_id", "selector"}
))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaRecoveryCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credential_id", nullable = false)
    private MfaCredentialEntity credential;

    @Column(nullable = false, length = 8)
    private String selector;

    @Column(name = "verifier_hash", nullable = false, length = 255)
    private String verifierHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Version
    private long version;
}
