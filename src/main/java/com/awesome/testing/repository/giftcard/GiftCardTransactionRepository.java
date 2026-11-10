package com.awesome.testing.repository.giftcard;

import com.awesome.testing.entity.giftcard.GiftCardTransactionEntity;
import com.awesome.testing.entity.giftcard.GiftCardTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface GiftCardTransactionRepository extends JpaRepository<GiftCardTransactionEntity, Long> {

    List<GiftCardTransactionEntity> findByGiftCardIdOrderByIdAsc(Long giftCardId);

    Optional<GiftCardTransactionEntity> findByGiftCardIdAndTypeAndReference(
            Long giftCardId, GiftCardTransactionType type, String reference);

    @Query("""
            select coalesce(sum(t.amount), 0) from GiftCardTransactionEntity t
            where t.giftCardId = :giftCardId and t.orderId = :orderId and t.type = :type
            """)
    BigDecimal sumForOrder(@Param("giftCardId") Long giftCardId, @Param("orderId") Long orderId,
                           @Param("type") GiftCardTransactionType type);
}
