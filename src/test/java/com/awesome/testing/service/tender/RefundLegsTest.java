package com.awesome.testing.service.tender;

import com.awesome.testing.entity.OrderEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RefundLegsTest {

    private final OrderEntity order = OrderEntity.builder()
            .totalAmount(new BigDecimal("100.00"))
            .giftCardAmount(new BigDecimal("30.00"))
            .pointsAmount(new BigDecimal("20.00"))
            .build();

    @Test
    void cardTakesTheRefundUpToWhatItPaid() {
        RefundLegs legs = RefundLegs.split(order, BigDecimal.ZERO, new BigDecimal("50.00"));

        assertThat(legs.card()).isEqualByComparingTo("50.00");
        assertThat(legs.tenders()).isEqualByComparingTo("0");
    }

    @Test
    void remainderBeyondTheCardGoesToTheTenders() {
        RefundLegs legs = RefundLegs.split(order, BigDecimal.ZERO, new BigDecimal("60.00"));

        assertThat(legs.card()).isEqualByComparingTo("50.00");
        assertThat(legs.tenders()).isEqualByComparingTo("10.00");
    }

    @Test
    void earlierRefundsUsedTheCardFirst() {
        RefundLegs partly = RefundLegs.split(order, new BigDecimal("45.00"), new BigDecimal("10.00"));
        RefundLegs exhausted = RefundLegs.split(order, new BigDecimal("60.00"), new BigDecimal("40.00"));

        assertThat(partly.card()).isEqualByComparingTo("5.00");
        assertThat(partly.tenders()).isEqualByComparingTo("5.00");
        assertThat(exhausted.card()).isEqualByComparingTo("0");
        assertThat(exhausted.tenders()).isEqualByComparingTo("40.00");
    }

    @Test
    void ordersWithoutTendersRefundEverythingToTheCard() {
        OrderEntity plain = OrderEntity.builder().totalAmount(new BigDecimal("40.00")).build();

        RefundLegs legs = RefundLegs.split(plain, new BigDecimal("10.00"), new BigDecimal("30.00"));

        assertThat(legs.card()).isEqualByComparingTo("30.00");
        assertThat(legs.tenders()).isEqualByComparingTo("0");
    }
}
