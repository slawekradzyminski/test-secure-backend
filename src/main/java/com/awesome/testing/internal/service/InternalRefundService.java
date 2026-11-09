package com.awesome.testing.internal.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.refund.OrderRefundEntity;
import com.awesome.testing.internal.dto.InternalOrderDto;
import com.awesome.testing.internal.dto.OrderRefundDto;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.refund.OrderRefundRepository;
import com.awesome.testing.service.coupon.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Books refunds for returned items on the order. The order row is locked so
 * concurrent refunds cannot together exceed the order total; a replayed
 * requestId returns the order unchanged. A refund that covers the whole order
 * gives the coupon redemption back to the customer.
 */
@Service
@RequiredArgsConstructor
public class InternalRefundService {

    private final OrderRepository orderRepository;
    private final OrderRefundRepository refundRepository;
    private final CouponService couponService;

    @Transactional
    public InternalOrderDto refund(Long orderId, OrderRefundDto request) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));
        Optional<OrderRefundEntity> previous = refundRepository.findByRequestId(request.requestId());
        if (previous.isPresent()) {
            OrderRefundEntity refund = previous.get();
            if (!refund.getOrder().getId().equals(orderId) || refund.getAmount().compareTo(request.amount()) != 0) {
                throw new CustomException("requestId already used with a different refund", HttpStatus.CONFLICT);
            }
            return InternalOrderDto.from(order);
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new CustomException("Only delivered orders can be refunded; order is " + order.getStatus(),
                    HttpStatus.CONFLICT);
        }
        BigDecimal alreadyRefunded = order.getRefundedAmount() == null ? BigDecimal.ZERO : order.getRefundedAmount();
        BigDecimal refundable = order.getTotalAmount().subtract(alreadyRefunded);
        if (request.amount().compareTo(refundable) > 0) {
            throw new CustomException("Refund of " + request.amount() + " exceeds the refundable amount of " + refundable,
                    HttpStatus.CONFLICT);
        }
        refundRepository.save(OrderRefundEntity.builder()
                .order(order)
                .requestId(request.requestId())
                .amount(request.amount())
                .reference(request.reference().trim())
                .build());
        order.setRefundedAmount(alreadyRefunded.add(request.amount()));
        if (order.getRefundedAmount().compareTo(order.getTotalAmount()) == 0) {
            couponService.releaseRedemption(order);
        }
        return InternalOrderDto.from(orderRepository.save(order));
    }
}
