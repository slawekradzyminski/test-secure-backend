package com.awesome.testing.repository.loyalty;

import com.awesome.testing.entity.loyalty.LoyaltyAccountEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccountEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoyaltyAccountEntity a where a.username = :username")
    Optional<LoyaltyAccountEntity> findByUsernameForUpdate(@Param("username") String username);

    @Modifying
    @Query("delete from LoyaltyAccountEntity a where a.username = :username")
    void deleteByUsername(@Param("username") String username);
}
