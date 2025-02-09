package com.awesome.testing.repository;

import com.awesome.testing.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.transaction.Transactional;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByUsername(String username);

    @Transactional
    void deleteByUsername(String username);

}
