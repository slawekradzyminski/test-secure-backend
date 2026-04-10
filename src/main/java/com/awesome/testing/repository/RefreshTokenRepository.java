package com.awesome.testing.repository;

import com.awesome.testing.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByToken(String token);

    long countByUserUsername(String username);

    void deleteByUserUsername(String username);
}
