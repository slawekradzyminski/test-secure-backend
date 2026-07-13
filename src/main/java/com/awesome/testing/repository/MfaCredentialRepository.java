package com.awesome.testing.repository;

import com.awesome.testing.entity.MfaCredentialEntity;
import com.awesome.testing.entity.UserEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MfaCredentialRepository extends JpaRepository<MfaCredentialEntity, Long> {

    Optional<MfaCredentialEntity> findByUserUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from MfaCredentialEntity credential where credential.user.id = :userId")
    Optional<MfaCredentialEntity> findByUserIdForUpdate(@Param("userId") Integer userId);

    void deleteByUser(UserEntity user);
}
