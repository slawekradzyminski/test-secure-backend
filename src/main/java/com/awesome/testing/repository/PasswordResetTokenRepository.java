package com.awesome.testing.repository;

import com.awesome.testing.entity.PasswordResetTokenEntity;
import com.awesome.testing.entity.UserEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordResetTokenEntity token where token.tokenHash = :tokenHash")
    Optional<PasswordResetTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    long countByUserUsername(String username);

    @Modifying
    @Query("delete from PasswordResetTokenEntity p where p.user = :user or p.expiresAt < :now")
    void deleteByUserOrExpired(@Param("user") UserEntity user, @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetTokenEntity p where p.user = :user")
    void deleteAllByUser(@Param("user") UserEntity user);

    @Modifying
    @Query("delete from PasswordResetTokenEntity p where p.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
