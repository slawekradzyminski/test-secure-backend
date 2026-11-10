package com.awesome.testing.repository.giftcard;

import com.awesome.testing.entity.giftcard.GiftCardEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GiftCardRepository extends JpaRepository<GiftCardEntity, Long>, JpaSpecificationExecutor<GiftCardEntity> {

    Optional<GiftCardEntity> findByCode(String code);

    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GiftCardEntity g where g.code = :code")
    Optional<GiftCardEntity> findByCodeForUpdate(@Param("code") String code);

    @Modifying
    @Query("update GiftCardEntity g set g.recipientUsername = null where g.recipientUsername = :username")
    void forgetRecipient(@Param("username") String username);
}
