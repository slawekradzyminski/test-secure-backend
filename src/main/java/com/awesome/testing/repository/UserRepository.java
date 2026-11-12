package com.awesome.testing.repository;

import com.awesome.testing.entity.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.transaction.Transactional;

import java.util.List;
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

    @Query("select count(u) > 0 from UserEntity u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoringCase(@Param("username") String username);

    @Query("select count(u) > 0 from UserEntity u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoringCase(@Param("email") String email);

    @Query("select count(u) > 0 from UserEntity u where lower(u.email) = lower(:email) and u.id <> :id")
    boolean existsByEmailIgnoringCaseAndIdNot(@Param("email") String email, @Param("id") Integer id);

    @Query("select u from UserEntity u where lower(u.username) = lower(:username)")
    List<UserEntity> findAllByUsernameIgnoringCase(@Param("username") String username);

    @Query("select u from UserEntity u where lower(u.email) = lower(:email)")
    List<UserEntity> findAllByEmailIgnoringCase(@Param("email") String email);

    /** id, username and email of every account, ordered by id (no roles, so no per-row collection load). */
    @Query("select u.id, u.username, u.email from UserEntity u order by u.id")
    List<Object[]> findAllIdentityColumns();

    @Transactional
    void deleteByUsername(String username);

}
