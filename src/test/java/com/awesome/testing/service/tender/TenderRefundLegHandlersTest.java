package com.awesome.testing.service.tender;

import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.service.payment.CardFirstRefundSplitter;
import com.awesome.testing.service.payment.RefundLeg;
import com.awesome.testing.service.payment.RefundLegType;
import com.awesome.testing.service.payment.RefundSplit;
import com.awesome.testing.service.payment.RefundSplitContext;
import com.awesome.testing.service.giftcard.GiftCardService;
import com.awesome.testing.service.loyalty.LoyaltyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The gift card and points legs plugged into the payments card-first splitter. */
@ExtendWith(MockitoExtension.class)
class TenderRefundLegHandlersTest {

    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Mock
    private GiftCardService giftCardService;
    @Mock
    private LoyaltyService loyaltyService;

    private final OrderEntity order = OrderEntity.builder().id(42L).username("buyer")
            .totalAmount(new BigDecimal("100.00")).giftCardCode("GC-ABCD-EFGH-JKMN")
            .giftCardAmount(new BigDecimal("30.00")).pointsRedeemed(2_000L).pointsAmount(new BigDecimal("20.00")).build();

    @Test
    void handlersDeclareTheirLegAndRunGiftCardFirst() {
        TenderRefundService service = new TenderRefundService(giftCardService, loyaltyService);
        List<Object> handlers = new ArrayList<>(List.of(
                new LoyaltyPointsRefundLegHandler(service), new GiftCardRefundLegHandler(service)));

        AnnotationAwareOrderComparator.sort(handlers);

        assertThat(handlers).extracting(handler -> handler.getClass().getSimpleName())
                .containsExactly("GiftCardRefundLegHandler", "LoyaltyPointsRefundLegHandler");
        assertThat(new GiftCardRefundLegHandler(service).type()).isEqualTo(RefundLegType.GIFT_CARD);
        assertThat(new LoyaltyPointsRefundLegHandler(service).type()).isEqualTo(RefundLegType.LOYALTY_POINTS);
    }

    @Test
    void cardTakesItsShareThenGiftCardThenPoints() {
        restorable("30.00", 2_000);

        RefundSplit split = splitter().split(context("60.00", "50.00"));

        assertThat(split.card()).isEqualByComparingTo("50.00");
        assertThat(split.additionalLegs()).containsExactly(new RefundLeg(RefundLegType.GIFT_CARD, new BigDecimal("10.00")));
    }

    @Test
    void adminPaidOrdersSendTheWholeRefundToGiftCardThenPoints() {
        restorable("30.00", 2_000);

        RefundSplit split = splitter().split(context("45.00", "0.00"));

        assertThat(split.card()).isEqualByComparingTo("0");
        assertThat(split.additionalLegs()).containsExactly(
                new RefundLeg(RefundLegType.GIFT_CARD, new BigDecimal("30.00")),
                new RefundLeg(RefundLegType.LOYALTY_POINTS, new BigDecimal("15.00")));
    }

    @Test
    void tendersNeverTakeMoreThanTheyPaid() {
        restorable("5.00", 300);

        RefundSplit split = splitter().split(context("40.00", "0.00"));

        assertThat(split.additionalLegs()).containsExactly(
                new RefundLeg(RefundLegType.GIFT_CARD, new BigDecimal("5.00")),
                new RefundLeg(RefundLegType.LOYALTY_POINTS, new BigDecimal("3.00")));
        assertThat(split.allocated()).isEqualByComparingTo("8.00");
    }

    @Test
    void bookingRestoresWithTheRefundReference() {
        TenderRefundService service = new TenderRefundService(giftCardService, loyaltyService);
        RefundSplitContext context = context("45.00", "0.00");

        new GiftCardRefundLegHandler(service).book(context, new BigDecimal("30.00"));
        new LoyaltyPointsRefundLegHandler(service).book(context, new BigDecimal("15.00"));

        verify(giftCardService).restore(order, new BigDecimal("30.00"), "refund:" + REQUEST);
        verify(loyaltyService).restoreRedeemed(order, 1_500, "refund:" + REQUEST);
        verify(loyaltyService, never()).revokeEarned(any(), any(), any());
    }

    private void restorable(String giftCard, long points) {
        lenient().when(giftCardService.restorable(order)).thenReturn(new BigDecimal(giftCard));
        lenient().when(loyaltyService.restorablePoints(order)).thenReturn(points);
    }

    private CardFirstRefundSplitter splitter() {
        TenderRefundService service = new TenderRefundService(giftCardService, loyaltyService);
        return new CardFirstRefundSplitter(List.of(new GiftCardRefundLegHandler(service), new LoyaltyPointsRefundLegHandler(service)));
    }

    private RefundSplitContext context(String amount, String cardRefundable) {
        return new RefundSplitContext(order, new BigDecimal(amount), new BigDecimal(cardRefundable), REQUEST, "Return #1");
    }
}
