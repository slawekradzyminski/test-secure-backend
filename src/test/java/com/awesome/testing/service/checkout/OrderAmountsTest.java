package com.awesome.testing.service.checkout;

import com.awesome.testing.entity.OrderEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderAmountsTest {

    @Test
    void amountDueSubtractsGiftCardAndPoints() {
        OrderEntity order = OrderEntity.builder().totalAmount(new BigDecimal("100.00"))
                .giftCardAmount(new BigDecimal("30.00")).pointsAmount(new BigDecimal("20.50")).build();

        assertThat(OrderAmounts.amountDue(order)).isEqualByComparingTo("49.50");
        assertThat(OrderAmounts.hasTenders(order)).isTrue();
    }

    @Test
    void eachTenderAloneCounts() {
        OrderEntity giftCard = OrderEntity.builder().totalAmount(BigDecimal.TEN).giftCardAmount(BigDecimal.ONE).build();
        OrderEntity points = OrderEntity.builder().totalAmount(BigDecimal.TEN).pointsAmount(BigDecimal.ONE).build();

        assertThat(OrderAmounts.amountDue(giftCard)).isEqualByComparingTo("9");
        assertThat(OrderAmounts.hasTenders(giftCard)).isTrue();
        assertThat(OrderAmounts.amountDue(points)).isEqualByComparingTo("9");
        assertThat(OrderAmounts.hasTenders(points)).isTrue();
    }

    @Test
    void withoutTendersAmountDueIsTheTotal() {
        OrderEntity order = OrderEntity.builder().totalAmount(new BigDecimal("12.34")).build();

        assertThat(OrderAmounts.amountDue(order)).isEqualByComparingTo("12.34");
        assertThat(OrderAmounts.hasTenders(order)).isFalse();
        assertThat(OrderAmounts.amountDue(OrderEntity.builder().build())).isEqualByComparingTo("0");
    }
}
