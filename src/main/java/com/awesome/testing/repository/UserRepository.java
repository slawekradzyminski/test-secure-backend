package com.awesome.testing.repository;

import com.awesome.testing.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.transaction.Transactional;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {

    boolean existsByUsername(String username);

    UserEntity findByUsername(String username);

    @Transactional
    void deleteByUsername(String username);

}
