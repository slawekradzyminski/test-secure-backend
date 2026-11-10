package com.awesome.testing.service.payment;

import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.payment.OrderPaymentEntity;
import com.awesome.testing.entity.payment.OrderPaymentStatus;
import com.awesome.testing.entity.refund.OrderRefundEntity;
import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.payment.OrderPaymentRepository;
import com.awesome.testing.repository.refund.OrderRefundRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Splits every newly booked refund ({@code REFUND_BOOKED}) with the
 * {@link RefundSplitter} and, still inside the booking transaction, queues the
 * card part in the {@link PaymentRefundOutbox} and books the other legs through
 * their {@link RefundLegHandler}. A plain {@link EventListener} on purpose: the
 * outbox row commits or rolls back together with the refund. Orders without a
 * captured payment (paid manually by an admin) get no card leg.
 */
@Component
public class RefundBookedListener {

    private final OrderRepository orderRepository;
    private final OrderRefundRepository refundRepository;
    private final OrderPaymentRepository paymentRepository;
    private final PaymentRefundOutbox outbox;
    private final RefundSplitter splitter;
    private final Map<RefundLegType, RefundLegHandler> handlers = new EnumMap<>(RefundLegType.class);

    @Autowired
    public RefundBookedListener(OrderRepository orderRepository, OrderRefundRepository refundRepository,
                                OrderPaymentRepository paymentRepository, PaymentRefundOutbox outbox,
                                RefundSplitter splitter, ObjectProvider<RefundLegHandler> handlers) {
        this(orderRepository, refundRepository, paymentRepository, outbox, splitter, handlers.orderedStream().toList());
    }

    public RefundBookedListener(OrderRepository orderRepository, OrderRefundRepository refundRepository,
                                OrderPaymentRepository paymentRepository, PaymentRefundOutbox outbox,
                                RefundSplitter splitter, List<RefundLegHandler> handlers) {
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.outbox = outbox;
        this.splitter = splitter;
        handlers.forEach(handler -> {
            if (this.handlers.putIfAbsent(handler.type(), handler) != null) {
                throw new IllegalStateException("Two refund leg handlers for " + handler.type());
            }
        });
    }

    @EventListener
    public void onRefundBooked(CommerceEvent event) {
        if (event.type() != CommerceEventType.REFUND_BOOKED) {
            return;
        }
        UUID requestId = (UUID) event.data().get("requestId");
        BigDecimal amount = (BigDecimal) event.data().get("amount");
        OrderEntity order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new IllegalStateException("Refunded order " + event.orderId() + " not found"));
        String reference = refundRepository.findByRequestId(requestId).map(OrderRefundEntity::getReference).orElse(null);
        Optional<OrderPaymentEntity> captured =
                paymentRepository.findFirstByOrderIdAndStatus(order.getId(), OrderPaymentStatus.CAPTURED);
        BigDecimal cardRefundable = captured
                .map(payment -> payment.getAmount().subtract(outbox.cardRefunded(order.getId())).max(BigDecimal.ZERO))
                .orElse(BigDecimal.ZERO);
        RefundSplitContext context = new RefundSplitContext(order, amount, cardRefundable, requestId, reference);
        RefundSplit split = splitter.split(context);
        check(split, context);
        if (split.card().signum() > 0) {
            outbox.enqueue(order.getId(), captured.orElseThrow().getPaymentId(), requestId, split.card(), reference);
        }
        for (RefundLeg leg : split.additionalLegs()) {
            RefundLegHandler handler = handlers.get(leg.type());
            if (handler == null) {
                throw new IllegalStateException("No refund leg handler for " + leg.type());
            }
            handler.book(context, leg.amount());
        }
    }

    /** Guards against a splitter that sends more than the refund, or more to the card than is refundable. */
    static void check(RefundSplit split, RefundSplitContext context) {
        if (split.card().signum() < 0 || split.card().compareTo(context.cardRefundable()) > 0
                || split.allocated().compareTo(context.amount()) > 0
                || split.additionalLegs().stream().anyMatch(leg -> leg.amount().signum() <= 0)) {
            throw new IllegalStateException("Invalid refund split " + split + " for " + context.amount()
                    + " with " + context.cardRefundable() + " refundable on the card");
        }
    }
}
