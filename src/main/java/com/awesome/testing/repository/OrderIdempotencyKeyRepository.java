package com.awesome.testing.repository;

import com.awesome.testing.entity.OrderIdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OrderIdempotencyKeyRepository extends JpaRepository<OrderIdempotencyKeyEntity, Long> {

    Optional<OrderIdempotencyKeyEntity> findByUsernameAndKey(String username, String key);

    @Modifying
    @Query("delete from OrderIdempotencyKeyEntity k where k.username = :username and k.key = :key and k.createdAt < :cutoff")
    int deleteExpired(@Param("username") String username, @Param("key") String key, @Param("cutoff") LocalDateTime cutoff);
}
