package com.awesome.testing.service.payment;

import com.awesome.testing.entity.OrderEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CardFirstRefundSplitterTest {

    @Test
    void wholeRefundGoesToTheCardWhileItIsRefundable() {
        RefundSplit split = new CardFirstRefundSplitter(List.of()).split(context("12.50", "30.00"));

        assertThat(split.card()).isEqualTo(new BigDecimal("12.50"));
        assertThat(split.additionalLegs()).isEmpty();
        assertThat(split.allocated()).isEqualTo(new BigDecimal("12.50"));
    }

    @Test
    void cardIsCappedAtWhatIsStillRefundableAndTheRestStaysUnallocatedWithoutHandlers() {
        RefundSplit exact = new CardFirstRefundSplitter(List.of()).split(context("30.00", "30.00"));
        RefundSplit capped = new CardFirstRefundSplitter(List.of()).split(context("30.00", "20.01"));
        RefundSplit nothingCaptured = new CardFirstRefundSplitter(List.of()).split(context("30.00", "0"));

        assertThat(exact.card()).isEqualTo(new BigDecimal("30.00"));
        assertThat(capped.card()).isEqualTo(new BigDecimal("20.01"));
        assertThat(capped.additionalLegs()).isEmpty();
        assertThat(nothingCaptured.card()).isEqualTo(new BigDecimal("0.00"));
        assertThat(new CardFirstRefundSplitter(List.of()).split(context("5.00", "-1.00")).card()).isZero();
        assertThat(new CardFirstRefundSplitter(List.of()).split(context("5.00", null)).card()).isZero();
    }

    @Test
    void remainderIsOfferedToHandlersInOrderAndClamped() {
        FixedHandler giftCard = new FixedHandler(RefundLegType.GIFT_CARD, "6.00");
        FixedHandler points = new FixedHandler(RefundLegType.LOYALTY_POINTS, "100.00");
        CardFirstRefundSplitter splitter = new CardFirstRefundSplitter(List.of(giftCard, points));

        RefundSplit split = splitter.split(context("30.00", "20.00"));

        assertThat(split.card()).isEqualTo(new BigDecimal("20.00"));
        assertThat(split.additionalLegs()).containsExactly(
                new RefundLeg(RefundLegType.GIFT_CARD, new BigDecimal("6.00")),
                new RefundLeg(RefundLegType.LOYALTY_POINTS, new BigDecimal("4.00")));
        assertThat(split.allocated()).isEqualTo(new BigDecimal("30.00"));
        assertThat(giftCard.offered).containsExactly(new BigDecimal("10.00"));
        assertThat(points.offered).containsExactly(new BigDecimal("4.00"));
    }

    @Test
    void handlersAreNotAskedWhenNothingRemainsAndNonPositiveAnswersAddNoLeg() {
        FixedHandler giftCard = new FixedHandler(RefundLegType.GIFT_CARD, "-1.00");
        FixedHandler points = new FixedHandler(RefundLegType.LOYALTY_POINTS, null);
        FixedHandler zero = new FixedHandler(RefundLegType.GIFT_CARD, "0.004");

        RefundSplit covered = new CardFirstRefundSplitter(List.of(giftCard)).split(context("10.00", "10.00"));
        RefundSplit declined = new CardFirstRefundSplitter(List.of(giftCard, points, zero)).split(context("10.00", "0"));

        assertThat(covered.additionalLegs()).isEmpty();
        assertThat(declined.additionalLegs()).isEmpty();
        assertThat(giftCard.offered).containsExactly(new BigDecimal("10.00"));
        assertThat(points.offered).containsExactly(new BigDecimal("10.00"));
        assertThat(zero.offered).containsExactly(new BigDecimal("10.00"));
    }

    private static RefundSplitContext context(String amount, String cardRefundable) {
        return new RefundSplitContext(new OrderEntity(), new BigDecimal(amount),
                cardRefundable == null ? null : new BigDecimal(cardRefundable), UUID.randomUUID(), "Return");
    }

    private static final class FixedHandler implements RefundLegHandler {
        private final RefundLegType type;
        private final BigDecimal answer;
        private final List<BigDecimal> offered = new ArrayList<>();

        private FixedHandler(RefundLegType type, String answer) {
            this.type = type;
            this.answer = answer == null ? null : new BigDecimal(answer);
        }

        @Override
        public RefundLegType type() {
            return type;
        }

        @Override
        public BigDecimal allocate(RefundSplitContext context, BigDecimal remaining) {
            offered.add(remaining);
            return answer;
        }

        @Override
        public void book(RefundSplitContext context, BigDecimal amount) {
        }
    }
}
