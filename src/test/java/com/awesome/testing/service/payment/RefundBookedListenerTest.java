package com.awesome.testing.service.payment;

import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.payment.OrderPaymentEntity;
import com.awesome.testing.entity.payment.OrderPaymentStatus;
import com.awesome.testing.entity.refund.OrderRefundEntity;
import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.payment.OrderPaymentRepository;
import com.awesome.testing.repository.refund.OrderRefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundBookedListenerTest {

    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID PAYMENT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Mock private OrderRepository orderRepository;
    @Mock private OrderRefundRepository refundRepository;
    @Mock private OrderPaymentRepository paymentRepository;
    @Mock private CardRefunds cardRefunds;

    private OrderEntity order;

    @BeforeEach
    void setUp() {
        order = OrderEntity.builder().id(5L).username("buyer").status(OrderStatus.DELIVERED)
                .totalAmount(new BigDecimal("50.00")).build();
        lenient().when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        lenient().when(refundRepository.findByRequestId(REQUEST))
                .thenReturn(Optional.of(OrderRefundEntity.builder().reference("Return #1").build()));
        lenient().when(cardRefunds.cardRefunded(5L)).thenReturn(new BigDecimal("15.00"));
    }

    @Test
    void requestsTheCardPartAgainstTheCapturedPayment() {
        captured("50.00");

        listener(new CardFirstRefundSplitter(List.of())).onRefundBooked(refundBooked("20.00"));

        verify(cardRefunds).request(5L, "buyer", PAYMENT, REQUEST, new BigDecimal("20.00"), "Return #1");
    }

    @Test
    void splitterSeesWhatIsLeftOnTheCard() {
        captured("30.00");
        List<RefundSplitContext> seen = new ArrayList<>();

        listener(context -> {
            seen.add(context);
            return RefundSplit.cardOnly(BigDecimal.ZERO);
        }).onRefundBooked(refundBooked("20.00"));

        assertThat(seen).singleElement().satisfies(context -> {
            assertThat(context.order()).isSameAs(order);
            assertThat(context.amount()).isEqualTo(new BigDecimal("20.00"));
            assertThat(context.cardRefundable()).isEqualTo(new BigDecimal("15.00"));
            assertThat(context.requestId()).isEqualTo(REQUEST);
            assertThat(context.reference()).isEqualTo("Return #1");
        });
        verify(cardRefunds, never()).request(any(), any(), any(), any(), any(), any());
    }

    @Test
    void cardRefundableNeverGoesNegative() {
        captured("10.00");
        List<RefundSplitContext> seen = new ArrayList<>();

        listener(context -> {
            seen.add(context);
            return RefundSplit.cardOnly(BigDecimal.ZERO);
        }).onRefundBooked(refundBooked("1.00"));

        assertThat(seen.getFirst().cardRefundable()).isZero();
    }

    @Test
    void ordersWithoutACapturedPaymentGetNoCardLeg() {
        when(paymentRepository.findFirstByOrderIdAndStatus(5L, OrderPaymentStatus.CAPTURED)).thenReturn(Optional.empty());
        when(refundRepository.findByRequestId(REQUEST)).thenReturn(Optional.empty());
        List<RefundSplitContext> seen = new ArrayList<>();

        listener(context -> {
            seen.add(context);
            return new CardFirstRefundSplitter(List.of()).split(context);
        }).onRefundBooked(refundBooked("20.00"));

        assertThat(seen.getFirst().cardRefundable()).isZero();
        assertThat(seen.getFirst().reference()).isNull();
        verify(cardRefunds, never()).request(any(), any(), any(), any(), any(), any());
        verify(cardRefunds, never()).cardRefunded(any());
    }

    @Test
    void additionalLegsAreBookedByTheirHandlers() {
        captured("50.00");
        RecordingHandler giftCard = new RecordingHandler(RefundLegType.GIFT_CARD);
        RecordingHandler points = new RecordingHandler(RefundLegType.LOYALTY_POINTS);
        RefundSplitter splitter = context -> new RefundSplit(new BigDecimal("5.00"), List.of(
                new RefundLeg(RefundLegType.LOYALTY_POINTS, new BigDecimal("3.00")),
                new RefundLeg(RefundLegType.GIFT_CARD, new BigDecimal("2.00"))));

        new RefundBookedListener(orderRepository, refundRepository, paymentRepository, cardRefunds, splitter,
                List.of(giftCard, points)).onRefundBooked(refundBooked("10.00"));

        verify(cardRefunds).request(5L, "buyer", PAYMENT, REQUEST, new BigDecimal("5.00"), "Return #1");
        assertThat(points.booked).containsExactly(new BigDecimal("3.00"));
        assertThat(giftCard.booked).containsExactly(new BigDecimal("2.00"));
    }

    @Test
    void aLegWithoutAHandlerOrAnInvalidSplitFailsTheBooking() {
        captured("50.00");
        RefundSplitter orphanLeg = context -> new RefundSplit(BigDecimal.ZERO,
                List.of(new RefundLeg(RefundLegType.GIFT_CARD, new BigDecimal("1.00"))));

        assertThatThrownBy(() -> listener(orphanLeg).onRefundBooked(refundBooked("10.00")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("GIFT_CARD");
        for (RefundSplit invalid : new RefundSplit[] {
                RefundSplit.cardOnly(new BigDecimal("-0.01")),
                RefundSplit.cardOnly(new BigDecimal("35.01")),
                new RefundSplit(new BigDecimal("5.00"), List.of(new RefundLeg(RefundLegType.GIFT_CARD, new BigDecimal("5.01")))),
                new RefundSplit(BigDecimal.ZERO, List.of(new RefundLeg(RefundLegType.GIFT_CARD, BigDecimal.ZERO)))}) {
            assertThatThrownBy(() -> listener(context -> invalid).onRefundBooked(refundBooked("10.00")))
                    .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("Invalid refund split");
        }
        verify(cardRefunds, never()).request(any(), any(), any(), any(), any(), any());
    }

    @Test
    void theBoundariesOfAValidSplitAreAccepted() {
        captured("50.00");
        RecordingHandler giftCard = new RecordingHandler(RefundLegType.GIFT_CARD);
        RefundSplitter fullCard = context -> RefundSplit.cardOnly(new BigDecimal("35.00"));
        RefundSplitter fullLegs = context -> new RefundSplit(new BigDecimal("4.00"),
                List.of(new RefundLeg(RefundLegType.GIFT_CARD, new BigDecimal("6.00"))));

        listener(fullCard).onRefundBooked(refundBooked("40.00"));
        new RefundBookedListener(orderRepository, refundRepository, paymentRepository, cardRefunds, fullLegs,
                List.of(giftCard)).onRefundBooked(refundBooked("10.00"));

        verify(cardRefunds).request(5L, "buyer", PAYMENT, REQUEST, new BigDecimal("35.00"), "Return #1");
        assertThat(giftCard.booked).containsExactly(new BigDecimal("6.00"));
    }

    @Test
    void aMissingOrderFailsTheBooking() {
        when(orderRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listener(new CardFirstRefundSplitter(List.of())).onRefundBooked(refundBooked("1.00")))
                .isInstanceOf(IllegalStateException.class).hasMessage("Refunded order 5 not found");
    }

    @Test
    void otherEventsAreIgnored() {
        listener(new CardFirstRefundSplitter(List.of())).onRefundBooked(new CommerceEvent(UUID.randomUUID(),
                CommerceEventType.ORDER_PAID, "buyer", 5L, Instant.now(), Map.of("orderId", 5L)));

        verifyNoInteractions(orderRepository, refundRepository, paymentRepository, cardRefunds);
    }

    @Test
    void twoHandlersForOneLegTypeAreRejected() {
        assertThatThrownBy(() -> new RefundBookedListener(orderRepository, refundRepository, paymentRepository, cardRefunds,
                new CardFirstRefundSplitter(List.of()),
                List.of(new RecordingHandler(RefundLegType.GIFT_CARD), new RecordingHandler(RefundLegType.GIFT_CARD))))
                .isInstanceOf(IllegalStateException.class).hasMessage("Two refund leg handlers for GIFT_CARD");
    }

    private void captured(String amount) {
        when(paymentRepository.findFirstByOrderIdAndStatus(5L, OrderPaymentStatus.CAPTURED)).thenReturn(Optional.of(
                OrderPaymentEntity.builder().paymentId(PAYMENT).amount(new BigDecimal(amount)).build()));
    }

    private RefundBookedListener listener(RefundSplitter splitter) {
        return new RefundBookedListener(orderRepository, refundRepository, paymentRepository, cardRefunds, splitter, List.of());
    }

    private static CommerceEvent refundBooked(String amount) {
        return new CommerceEvent(UUID.randomUUID(), CommerceEventType.REFUND_BOOKED, "buyer", 5L, Instant.now(),
                Map.of("orderId", 5L, "status", "DELIVERED", "totalAmount", new BigDecimal("50.00"),
                        "amount", new BigDecimal(amount), "requestId", REQUEST));
    }

    private static final class RecordingHandler implements RefundLegHandler {
        private final RefundLegType type;
        private final List<BigDecimal> booked = new ArrayList<>();

        private RecordingHandler(RefundLegType type) {
            this.type = type;
        }

        @Override
        public RefundLegType type() {
            return type;
        }

        @Override
        public BigDecimal allocate(RefundSplitContext context, BigDecimal remaining) {
            return BigDecimal.ZERO;
        }

        @Override
        public void book(RefundSplitContext context, BigDecimal amount) {
            booked.add(amount);
        }
    }
}
