package com.awesome.testing.repository;

import com.awesome.testing.entity.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.transaction.Transactional;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {

    Optional<UserEntity> findByUsername(String username);

    /**
     * Locks the user row for deletion. Writers that share-lock the row first ({@link #findByIdForShare})
     * wait for the deleting transaction and then find the user gone.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.username = :username")
    Optional<UserEntity> findByUsernameForUpdate(@Param("username") String username);

    /** Share-locks the user row before inserting a row that references it; empty once the user is deleted. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForShare(@Param("id") Integer id);

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByAuthProviderAndProviderSubject(String authProvider, String providerSubject);

    Optional<UserEntity> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Transactional
    void deleteByUsername(String username);

}
