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
import com.awesome.testing.service.checkout.OrderAmounts;
import com.awesome.testing.service.notification.CustomerNotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies payment results reported by payment-service. The order row is locked,
 * so concurrent notifications for one order are serialized, and each requestId
 * is handled once: a replay gets the first answer.
 * <ul>
 *   <li>CAPTURED moves a PENDING order to PAID when the amount equals
 *       {@link OrderAmounts#amountDue}, publishes ORDER_PAID and emails
 *       PAYMENT_RECEIVED. The same payment again is a 200 no-op; a cancelled
 *       order or one paid otherwise is 409 so payment-service voids the capture.</li>
 *   <li>FAILED keeps the order as it is; for a PENDING order it publishes
 *       PAYMENT_FAILED and emails PAYMENT_FAILED.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class InternalPaymentService {

    static final String NOT_PAYABLE = "Order is not payable";
    static final String DEFAULT_CURRENCY = "USD";

    private final OrderRepository orderRepository;
    private final OrderPaymentRepository paymentRepository;
    private final PaymentNotificationRepository notificationRepository;
    private final CommerceEvents commerceEvents;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock commerceClock;

    @Transactional
    public PaymentResultResponseDto apply(Long orderId, PaymentResultDto result) {
        if (result.status() == OrderPaymentStatus.FAILED && (result.failureCode() == null || result.failureCode().isBlank())) {
            throw new CustomException("failureCode is required for FAILED payments", HttpStatus.BAD_REQUEST);
        }
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));
        Optional<PaymentNotificationEntity> previous = notificationRepository.findByRequestId(result.requestId());
        if (previous.isPresent()) {
            return replay(previous.get(), orderId, result);
        }
        OrderStatus answered = result.status() == OrderPaymentStatus.CAPTURED
                ? capture(order, result)
                : fail(order, result);
        notificationRepository.save(PaymentNotificationEntity.builder()
                .requestId(result.requestId())
                .orderId(orderId)
                .paymentId(result.paymentId())
                .status(result.status())
                .orderStatus(answered)
                .createdAt(commerceClock.instant())
                .build());
        return new PaymentResultResponseDto(orderId, answered);
    }

    private static PaymentResultResponseDto replay(PaymentNotificationEntity previous, Long orderId, PaymentResultDto result) {
        if (!previous.getOrderId().equals(orderId)
                || !previous.getPaymentId().equals(result.paymentId())
                || previous.getStatus() != result.status()) {
            throw new CustomException("requestId already used with a different payment result", HttpStatus.CONFLICT);
        }
        return new PaymentResultResponseDto(orderId, previous.getOrderStatus());
    }

    private OrderStatus capture(OrderEntity order, PaymentResultDto result) {
        Optional<OrderPaymentEntity> captured =
                paymentRepository.findFirstByOrderIdAndStatus(order.getId(), OrderPaymentStatus.CAPTURED);
        if (captured.isPresent()) {
            if (captured.get().getPaymentId().equals(result.paymentId())) {
                return order.getStatus();
            }
            throw new CustomException(NOT_PAYABLE, HttpStatus.CONFLICT);
        }
        if (order.getStatus() != OrderStatus.PENDING || paymentRepository.findByPaymentId(result.paymentId()).isPresent()) {
            throw new CustomException(NOT_PAYABLE, HttpStatus.CONFLICT);
        }
        BigDecimal due = OrderAmounts.amountDue(order);
        if (result.amount().compareTo(due) != 0) {
            throw new CustomException("Payment amount " + money(result.amount()) + " does not match the amount due "
                    + money(due), HttpStatus.UNPROCESSABLE_CONTENT);
        }
        order.setStatus(OrderStatus.PAID);
        OrderEntity saved = orderRepository.save(order);
        String currency = currency(result);
        record(saved, result, currency);
        commerceEvents.publish(CommerceEventType.ORDER_PAID, saved, Map.of("paymentId", result.paymentId()));
        email(saved, result, EmailTemplate.PAYMENT_RECEIVED, Map.of(
                "orderId", String.valueOf(saved.getId()),
                "amount", money(result.amount()),
                "currency", currency));
        return saved.getStatus();
    }

    private OrderStatus fail(OrderEntity order, PaymentResultDto result) {
        if (paymentRepository.findByPaymentId(result.paymentId()).isPresent()) {
            return order.getStatus();
        }
        record(order, result, currency(result));
        if (order.getStatus() == OrderStatus.PENDING) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("paymentId", result.paymentId());
            extra.put("failureCode", result.failureCode());
            commerceEvents.publish(CommerceEventType.PAYMENT_FAILED, order, extra);
            email(order, result, EmailTemplate.PAYMENT_FAILED, Map.of(
                    "orderId", String.valueOf(order.getId()),
                    "failureCode", result.failureCode()));
        }
        return order.getStatus();
    }

    private void record(OrderEntity order, PaymentResultDto result, String currency) {
        paymentRepository.save(OrderPaymentEntity.builder()
                .orderId(order.getId())
                .paymentId(result.paymentId())
                .status(result.status())
                .amount(result.amount().setScale(2, RoundingMode.HALF_UP))
                .currency(currency)
                .failureCode(result.status() == OrderPaymentStatus.FAILED ? result.failureCode() : null)
                .createdAt(commerceClock.instant())
                .build());
    }

    /** Queued after commit by the notification listener; deduplicated by a requestId derived from the result's. */
    private void email(OrderEntity order, PaymentResultDto result, EmailTemplate template, Map<String, String> properties) {
        UUID emailRequestId = UUID.nameUUIDFromBytes(
                ("payment-result:" + result.requestId()).getBytes(StandardCharsets.UTF_8));
        eventPublisher.publishEvent(new CustomerNotificationRequestedEvent(
                emailRequestId, order.getUsername(), template, properties));
    }

    private static String currency(PaymentResultDto result) {
        return result.currency() == null ? DEFAULT_CURRENCY : result.currency();
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
