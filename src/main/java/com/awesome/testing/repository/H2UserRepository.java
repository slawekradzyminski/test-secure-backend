package com.awesome.testing.repository;

import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import com.awesome.testing.entities.user.H2UserEntity;

@Profile("dev")
public interface H2UserRepository extends JpaRepository<H2UserEntity, Integer>, IUserRepository<H2UserEntity> {

    @Override
    boolean existsByUsername(String username);

    @Override
    H2UserEntity findByUsername(String username);

    @Override
    @Transactional
    void deleteByUsername(String username);

}
