package com.awesome.testing.repository;

import com.awesome.testing.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.transaction.Transactional;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {

    Optional<UserEntity> findByUsername(String username);

    @Transactional
    void deleteByUsername(String username);

}
