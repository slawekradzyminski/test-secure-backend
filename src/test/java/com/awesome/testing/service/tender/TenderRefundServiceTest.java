package com.awesome.testing.service.tender;

import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.service.giftcard.GiftCardService;
import com.awesome.testing.service.loyalty.LoyaltyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderRefundServiceTest {

    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private GiftCardService giftCardService;
    @Mock
    private LoyaltyService loyaltyService;

    @InjectMocks
    private TenderRefundService service;

    private final OrderEntity order = OrderEntity.builder().id(42L).username("buyer")
            .totalAmount(new BigDecimal("100.00")).giftCardCode("GC-ABCD-EFGH-JKMN")
            .giftCardAmount(new BigDecimal("30.00")).pointsRedeemed(2_000L).pointsAmount(new BigDecimal("20.00")).build();

    @Test
    void refundsTheGiftCardFirstAndPointsWithTheRest() {
        when(giftCardService.restore(order, new BigDecimal("40.00"), "refund:" + REQUEST)).thenReturn(new BigDecimal("25.00"));
        when(loyaltyService.restoreRedeemed(order, 1_500, "refund:" + REQUEST)).thenReturn(1_500L);

        TenderRefund refund = service.refundToTenders(order, REQUEST, new BigDecimal("40"));

        assertThat(refund).isEqualTo(new TenderRefund(new BigDecimal("25.00"), 1_500, new BigDecimal("15.00"), new BigDecimal("0.00")));
    }

    @Test
    void reportsWhatNeitherTenderCouldTake() {
        when(giftCardService.restore(order, new BigDecimal("80.00"), "refund:" + REQUEST)).thenReturn(new BigDecimal("30.00"));
        when(loyaltyService.restoreRedeemed(order, 5_000, "refund:" + REQUEST)).thenReturn(2_000L);

        TenderRefund refund = service.refundToTenders(order, REQUEST, new BigDecimal("80.00"));

        assertThat(refund.unallocated()).isEqualByComparingTo("30.00");
        assertThat(refund.pointsAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void cancellationRestoresEverythingTheTendersPaid() {
        when(giftCardService.restore(order, new BigDecimal("50.00"), "cancel:order:42")).thenReturn(new BigDecimal("30.00"));
        when(loyaltyService.restoreRedeemed(order, 2_000, "cancel:order:42")).thenReturn(2_000L);

        TenderRefund refund = service.restoreForCancellation(order);

        assertThat(refund.giftCardAmount()).isEqualByComparingTo("30.00");
        assertThat(refund.points()).isEqualTo(2_000);
        assertThat(refund.unallocated()).isEqualByComparingTo("0");
    }

    @Test
    void cancellationOfASingleTenderOrderUsesItsAmount() {
        OrderEntity pointsOnly = OrderEntity.builder().id(43L).totalAmount(BigDecimal.TEN)
                .pointsRedeemed(500L).pointsAmount(new BigDecimal("5.00")).build();
        OrderEntity giftCardOnly = OrderEntity.builder().id(44L).totalAmount(BigDecimal.TEN)
                .giftCardCode("GC-ABCD-EFGH-JKMN").giftCardAmount(new BigDecimal("7.00")).build();
        when(giftCardService.restore(pointsOnly, new BigDecimal("5.00"), "cancel:order:43")).thenReturn(new BigDecimal("0.00"));
        when(loyaltyService.restoreRedeemed(pointsOnly, 500, "cancel:order:43")).thenReturn(500L);
        when(giftCardService.restore(giftCardOnly, new BigDecimal("7.00"), "cancel:order:44")).thenReturn(new BigDecimal("7.00"));
        when(loyaltyService.restoreRedeemed(giftCardOnly, 0, "cancel:order:44")).thenReturn(0L);

        assertThat(service.restoreForCancellation(pointsOnly).points()).isEqualTo(500);
        assertThat(service.restoreForCancellation(giftCardOnly).giftCardAmount()).isEqualByComparingTo("7.00");
    }

    @Test
    void perLegMethodsCapAtWhatEachTenderCanTakeBack() {
        when(giftCardService.restorable(order)).thenReturn(new BigDecimal("30.00"));
        when(loyaltyService.restorablePoints(order)).thenReturn(1_500L);

        assertThat(service.giftCardRefundable(order, new BigDecimal("40.00"))).isEqualByComparingTo("30.00");
        assertThat(service.giftCardRefundable(order, new BigDecimal("12.00"))).isEqualByComparingTo("12.00");
        assertThat(service.pointsRefundable(order, new BigDecimal("40.00"))).isEqualByComparingTo("15.00");
        assertThat(service.pointsRefundable(order, new BigDecimal("2.50"))).isEqualByComparingTo("2.50");
    }

    @Test
    void perLegBookingUsesTheRefundReference() {
        when(giftCardService.restore(order, new BigDecimal("12.50"), "refund:" + REQUEST)).thenReturn(new BigDecimal("12.50"));
        when(loyaltyService.restoreRedeemed(order, 250, "refund:" + REQUEST)).thenReturn(250L);

        assertThat(service.refundToGiftCard(order, REQUEST, new BigDecimal("12.5"))).isEqualByComparingTo("12.50");
        assertThat(service.refundToPoints(order, REQUEST, new BigDecimal("2.50"))).isEqualTo(250);
    }
}
