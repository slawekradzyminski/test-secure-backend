package com.awesome.testing.repository;

import com.awesome.testing.entity.MfaCredentialEntity;
import com.awesome.testing.entity.MfaRecoveryCodeEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCodeEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select code from MfaRecoveryCodeEntity code
            where code.credential.id = :credentialId and code.selector = :selector and code.usedAt is null
            """)
    Optional<MfaRecoveryCodeEntity> findUnusedForUpdate(
            @Param("credentialId") Long credentialId,
            @Param("selector") String selector);

    long countByCredentialAndUsedAtIsNull(MfaCredentialEntity credential);

    long countByCredentialUserUsername(String username);

    void deleteByCredential(MfaCredentialEntity credential);
}
