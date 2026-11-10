package com.awesome.testing.internal.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.payment.PaymentResultDto;
import com.awesome.testing.dto.payment.PaymentResultResponseDto;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.payment.OrderPaymentEntity;
import com.awesome.testing.entity.payment.OrderPaymentStatus;
import com.awesome.testing.entity.payment.PaymentNotificationEntity;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.event.CommerceEvents;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.payment.OrderPaymentRepository;
import com.awesome.testing.repository.payment.PaymentNotificationRepository;
import com.awesome.testing.service.notification.CustomerNotificationRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
class InternalPaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID PAYMENT = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Mock private OrderRepository orderRepository;
    @Mock private OrderPaymentRepository paymentRepository;
    @Mock private PaymentNotificationRepository notificationRepository;
    @Mock private CommerceEvents commerceEvents;
    @Mock private ApplicationEventPublisher eventPublisher;

    private InternalPaymentService service;
    private OrderEntity order;

    @BeforeEach
    void setUp() {
        service = new InternalPaymentService(orderRepository, paymentRepository, notificationRepository,
                commerceEvents, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
        order = OrderEntity.builder().id(9L).username("buyer").status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("40.00")).build();
        lenient().when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(order));
        lenient().when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(notificationRepository.findByRequestId(any())).thenReturn(Optional.empty());
        lenient().when(paymentRepository.findFirstByOrderIdAndStatus(any(), any())).thenReturn(Optional.empty());
        lenient().when(paymentRepository.findByPaymentId(any())).thenReturn(Optional.empty());
    }

    @Test
    void captureMarksThePendingOrderPaidAndRecordsEverything() {
        PaymentResultResponseDto response = service.apply(9L, captured("40.00"));

        assertThat(response).isEqualTo(new PaymentResultResponseDto(9L, OrderStatus.PAID));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).save(order);
        verify(commerceEvents).publish(CommerceEventType.ORDER_PAID, order, Map.of("paymentId", PAYMENT));
        OrderPaymentEntity payment = savedPayment();
        assertThat(payment.getOrderId()).isEqualTo(9L);
        assertThat(payment.getPaymentId()).isEqualTo(PAYMENT);
        assertThat(payment.getStatus()).isEqualTo(OrderPaymentStatus.CAPTURED);
        assertThat(payment.getAmount()).isEqualTo(new BigDecimal("40.00"));
        assertThat(payment.getCurrency()).isEqualTo("USD");
        assertThat(payment.getFailureCode()).isNull();
        assertThat(payment.getCreatedAt()).isEqualTo(NOW);
        PaymentNotificationEntity notification = savedNotification();
        assertThat(notification.getRequestId()).isEqualTo(REQUEST);
        assertThat(notification.getOrderId()).isEqualTo(9L);
        assertThat(notification.getPaymentId()).isEqualTo(PAYMENT);
        assertThat(notification.getStatus()).isEqualTo(OrderPaymentStatus.CAPTURED);
        assertThat(notification.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(notification.getCreatedAt()).isEqualTo(NOW);
        CustomerNotificationRequestedEvent email = email();
        assertThat(email.template()).isEqualTo(EmailTemplate.PAYMENT_RECEIVED);
        assertThat(email.username()).isEqualTo("buyer");
        assertThat(email.requestId()).isEqualTo(
                UUID.nameUUIDFromBytes(("payment-result:" + REQUEST).getBytes(StandardCharsets.UTF_8)));
        assertThat(email.properties()).isEqualTo(Map.of("orderId", "9", "amount", "40.00", "currency", "USD"));
    }

    @Test
    void captureKeepsAnExplicitCurrencyAndComparesAmountsByValue() {
        service.apply(9L, new PaymentResultDto(REQUEST, PAYMENT, OrderPaymentStatus.CAPTURED,
                new BigDecimal("40.0"), "EUR", null));

        assertThat(savedPayment().getCurrency()).isEqualTo("EUR");
        assertThat(savedPayment().getAmount()).isEqualTo(new BigDecimal("40.00"));
        assertThat(email().properties()).containsEntry("currency", "EUR").containsEntry("amount", "40.00");
    }

    @Test
    void theSameCapturedPaymentAgainIsANoOp() {
        order.setStatus(OrderStatus.SHIPPED);
        when(paymentRepository.findFirstByOrderIdAndStatus(9L, OrderPaymentStatus.CAPTURED))
                .thenReturn(Optional.of(OrderPaymentEntity.builder().paymentId(PAYMENT).build()));

        PaymentResultResponseDto response = service.apply(9L, captured("1.00"));

        assertThat(response.orderStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(orderRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(commerceEvents, eventPublisher);
        assertThat(savedNotification().getOrderStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void anotherCapturedPaymentIsNotPayable() {
        when(paymentRepository.findFirstByOrderIdAndStatus(9L, OrderPaymentStatus.CAPTURED))
                .thenReturn(Optional.of(OrderPaymentEntity.builder().paymentId(UUID.randomUUID()).build()));

        assertConflict(() -> service.apply(9L, captured("40.00")), "Order is not payable");
    }

    @Test
    void nonPendingOrdersAreNotPayable() {
        for (OrderStatus status : new OrderStatus[] {OrderStatus.PAID, OrderStatus.CANCELLED, OrderStatus.DELIVERED}) {
            order.setStatus(status);
            assertConflict(() -> service.apply(9L, captured("40.00")), "Order is not payable");
        }
        verifyNoInteractions(commerceEvents, eventPublisher);
    }

    @Test
    void aPaymentThatAlreadyFailedCannotCaptureLater() {
        when(paymentRepository.findByPaymentId(PAYMENT)).thenReturn(Optional.of(
                OrderPaymentEntity.builder().paymentId(PAYMENT).status(OrderPaymentStatus.FAILED).build()));

        assertConflict(() -> service.apply(9L, captured("40.00")), "Order is not payable");
    }

    @Test
    void captureAmountMustEqualTheAmountDue() {
        assertThatThrownBy(() -> service.apply(9L, captured("39.99")))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(e.getMessage()).isEqualTo("Payment amount 39.99 does not match the amount due 40.00");
                });
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void failureOfAPendingOrderPublishesAndEmailsButKeepsTheOrder() {
        PaymentResultResponseDto response = service.apply(9L, failed("CARD_DECLINED"));

        assertThat(response).isEqualTo(new PaymentResultResponseDto(9L, OrderStatus.PENDING));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
        verify(commerceEvents).publish(CommerceEventType.PAYMENT_FAILED, order,
                Map.of("paymentId", PAYMENT, "failureCode", "CARD_DECLINED"));
        OrderPaymentEntity payment = savedPayment();
        assertThat(payment.getStatus()).isEqualTo(OrderPaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("CARD_DECLINED");
        CustomerNotificationRequestedEvent email = email();
        assertThat(email.template()).isEqualTo(EmailTemplate.PAYMENT_FAILED);
        assertThat(email.properties()).isEqualTo(Map.of("orderId", "9", "failureCode", "CARD_DECLINED"));
        assertThat(savedNotification().getStatus()).isEqualTo(OrderPaymentStatus.FAILED);
    }

    @Test
    void failureOfAnOrderNoLongerPendingIsRecordedQuietly() {
        order.setStatus(OrderStatus.PAID);

        PaymentResultResponseDto response = service.apply(9L, failed("CARD_DECLINED"));

        assertThat(response.orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(savedPayment().getStatus()).isEqualTo(OrderPaymentStatus.FAILED);
        verifyNoInteractions(commerceEvents, eventPublisher);
    }

    @Test
    void aKnownPaymentFailingAgainChangesNothing() {
        when(paymentRepository.findByPaymentId(PAYMENT)).thenReturn(Optional.of(new OrderPaymentEntity()));

        PaymentResultResponseDto response = service.apply(9L, failed("CARD_DECLINED"));

        assertThat(response.orderStatus()).isEqualTo(OrderStatus.PENDING);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(commerceEvents, eventPublisher);
    }

    @Test
    void failureNeedsAFailureCode() {
        for (String code : new String[] {null, " "}) {
            assertThatThrownBy(() -> service.apply(9L, failed(code)))
                    .isInstanceOfSatisfying(CustomException.class, e -> {
                        assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.getMessage()).isEqualTo("failureCode is required for FAILED payments");
                    });
        }
        verifyNoInteractions(orderRepository);
    }

    @Test
    void unknownOrdersAreNotFound() {
        assertThatThrownBy(() -> service.apply(404L, captured("40.00")))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void aReplayedRequestReturnsTheFirstAnswerWithoutSideEffects() {
        when(notificationRepository.findByRequestId(REQUEST)).thenReturn(Optional.of(notification(9L, PAYMENT,
                OrderPaymentStatus.CAPTURED)));

        PaymentResultResponseDto response = service.apply(9L, captured("1.00"));

        assertThat(response).isEqualTo(new PaymentResultResponseDto(9L, OrderStatus.PAID));
        verify(notificationRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(commerceEvents, eventPublisher);
    }

    @Test
    void aRequestIdReusedForAnotherResultIsAConflict() {
        for (PaymentNotificationEntity previous : new PaymentNotificationEntity[] {
                notification(8L, PAYMENT, OrderPaymentStatus.CAPTURED),
                notification(9L, UUID.randomUUID(), OrderPaymentStatus.CAPTURED),
                notification(9L, PAYMENT, OrderPaymentStatus.FAILED)}) {
            when(notificationRepository.findByRequestId(REQUEST)).thenReturn(Optional.of(previous));
            assertConflict(() -> service.apply(9L, captured("40.00")),
                    "requestId already used with a different payment result");
        }
    }

    private static PaymentNotificationEntity notification(Long orderId, UUID paymentId, OrderPaymentStatus status) {
        return PaymentNotificationEntity.builder().requestId(REQUEST).orderId(orderId).paymentId(paymentId)
                .status(status).orderStatus(OrderStatus.PAID).build();
    }

    private static PaymentResultDto captured(String amount) {
        return new PaymentResultDto(REQUEST, PAYMENT, OrderPaymentStatus.CAPTURED, new BigDecimal(amount), null, null);
    }

    private static PaymentResultDto failed(String failureCode) {
        return new PaymentResultDto(REQUEST, PAYMENT, OrderPaymentStatus.FAILED, new BigDecimal("40.00"), "USD", failureCode);
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getMessage()).isEqualTo(message);
        });
    }

    private OrderPaymentEntity savedPayment() {
        ArgumentCaptor<OrderPaymentEntity> captor = ArgumentCaptor.forClass(OrderPaymentEntity.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private PaymentNotificationEntity savedNotification() {
        ArgumentCaptor<PaymentNotificationEntity> captor = ArgumentCaptor.forClass(PaymentNotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    private CustomerNotificationRequestedEvent email() {
        ArgumentCaptor<CustomerNotificationRequestedEvent> captor =
                ArgumentCaptor.forClass(CustomerNotificationRequestedEvent.class);
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
        return captor.getValue();
    }
}
