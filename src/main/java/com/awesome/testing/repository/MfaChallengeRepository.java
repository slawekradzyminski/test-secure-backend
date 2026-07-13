package com.awesome.testing.repository;

import com.awesome.testing.entity.MfaChallengeEntity;
import com.awesome.testing.entity.UserEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MfaChallengeRepository extends JpaRepository<MfaChallengeEntity, Long> {

    Optional<MfaChallengeEntity> findFirstByUserUsernameOrderByCreatedAtDesc(String username);

    long countByUserUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from MfaChallengeEntity challenge where challenge.tokenHash = :tokenHash")
    Optional<MfaChallengeEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    void deleteByUser(UserEntity user);
}
