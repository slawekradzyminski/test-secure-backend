package com.awesome.testing.service.tender;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TenderSplitTest {

    @Test
    void giftCardPaysFirstUpToItsBalance() {
        TenderSplit split = TenderSplit.split(new BigDecimal("50.00"), new BigDecimal("30.00"), null);

        assertThat(split.giftCardAmount()).hasToString("30.00");
        assertThat(split.pointsRedeemed()).isNull();
        assertThat(split.pointsAmount()).isNull();
        assertThat(split.amountDue()).hasToString("20.00");
    }

    @Test
    void giftCardIsCappedAtTheTotal() {
        TenderSplit split = TenderSplit.split(new BigDecimal("50"), new BigDecimal("80.00"), null);

        assertThat(split.giftCardAmount()).hasToString("50.00");
        assertThat(split.amountDue()).hasToString("0.00");
    }

    @Test
    void pointsPayWhatTheGiftCardLeaves() {
        TenderSplit split = TenderSplit.split(new BigDecimal("29.00"), new BigDecimal("10.00"), 1_000L);

        assertThat(split.giftCardAmount()).hasToString("10.00");
        assertThat(split.pointsRedeemed()).isEqualTo(1_000L);
        assertThat(split.pointsAmount()).hasToString("10.00");
        assertThat(split.amountDue()).hasToString("9.00");
    }

    @Test
    void pointsAreCappedToTheCentWhenTheRemainderIsSmaller() {
        TenderSplit split = TenderSplit.split(new BigDecimal("12.34"), null, 5_000L);

        assertThat(split.giftCardAmount()).isNull();
        assertThat(split.pointsRedeemed()).isEqualTo(1_234L);
        assertThat(split.pointsAmount()).hasToString("12.34");
        assertThat(split.amountDue()).hasToString("0.00");
    }

    @Test
    void unusableCardsAndZeroTotalsTakeNothing() {
        TenderSplit unusable = TenderSplit.split(new BigDecimal("20.00"), BigDecimal.ZERO, 100L);
        TenderSplit free = TenderSplit.split(BigDecimal.ZERO, new BigDecimal("10.00"), 100L);
        TenderSplit negative = TenderSplit.split(new BigDecimal("-1.00"), new BigDecimal("-5.00"), -100L);

        assertThat(unusable.giftCardAmount()).hasToString("0.00");
        assertThat(unusable.pointsRedeemed()).isEqualTo(100L);
        assertThat(unusable.amountDue()).hasToString("19.00");
        assertThat(free.giftCardAmount()).hasToString("0.00");
        assertThat(free.pointsRedeemed()).isZero();
        assertThat(free.pointsAmount()).hasToString("0.00");
        assertThat(free.amountDue()).hasToString("0.00");
        assertThat(negative.giftCardAmount()).hasToString("0.00");
        assertThat(negative.pointsRedeemed()).isZero();
        assertThat(negative.amountDue()).hasToString("0.00");
    }
}
