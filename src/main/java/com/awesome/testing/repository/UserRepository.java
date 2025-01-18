package com.awesome.testing.repository;

import com.awesome.testing.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.transaction.Transactional;

public interface UserRepository extends JpaRepository<User, Integer> {

    boolean existsByUsername(String username);

    User findByUsername(String username);

    @Transactional
    void deleteByUsername(String username);

}
