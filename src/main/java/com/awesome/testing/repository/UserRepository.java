package com.awesome.testing.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

import com.awesome.testing.entities.user.UserEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {

    boolean existsByUsername(String username);

    UserEntity findByUsername(String username);

    @Transactional
    void deleteByUsername(String username);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.specialties WHERE u.username IN :usernames")
    List<UserEntity> findAllWithSpecialtiesByUsername(@Param("usernames") Set<String> usernames);

}
