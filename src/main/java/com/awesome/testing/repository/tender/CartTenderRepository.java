package com.awesome.testing.repository.tender;

import com.awesome.testing.entity.tender.CartTenderEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartTenderRepository extends JpaRepository<CartTenderEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CartTenderEntity t where t.username = :username")
    Optional<CartTenderEntity> findByUsernameForUpdate(@Param("username") String username);

    @Modifying
    @Query("delete from CartTenderEntity t where t.username = :username")
    void deleteByUsername(@Param("username") String username);
}
