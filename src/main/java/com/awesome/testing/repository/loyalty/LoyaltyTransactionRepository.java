package com.awesome.testing.repository.loyalty;

import com.awesome.testing.entity.loyalty.LoyaltyTransactionEntity;
import com.awesome.testing.entity.loyalty.LoyaltyTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransactionEntity, Long> {

    Page<LoyaltyTransactionEntity> findByUsername(String username, Pageable pageable);

    Optional<LoyaltyTransactionEntity> findByUsernameAndTypeAndReference(
            String username, LoyaltyTransactionType type, String reference);

    @Query("""
            select coalesce(sum(t.points), 0) from LoyaltyTransactionEntity t
            where t.username = :username and t.orderId = :orderId and t.type = :type
            """)
    long sumForOrder(@Param("username") String username, @Param("orderId") Long orderId,
                     @Param("type") LoyaltyTransactionType type);

    @Modifying
    @Query("delete from LoyaltyTransactionEntity t where t.username = :username")
    void deleteByUsername(@Param("username") String username);
}
