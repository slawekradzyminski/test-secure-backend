package com.awesome.testing.service.tender;

import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.service.OrderService;
import com.awesome.testing.service.checkout.OrderAmounts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Marks orders fully paid by gift card and points as PAID; see {@link FullyTenderedOrderListener}. */
@Service
@RequiredArgsConstructor
public class TenderSettlementService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /**
     * Locks the order and moves it from PENDING to PAID when tenders cover it
     * completely. Anything else (already paid or cancelled, money still due) is left alone.
     *
     * @return true when the order was marked PAID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean settleIfFullyTendered(Long orderId) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING || !OrderAmounts.hasTenders(order)
                || OrderAmounts.amountDue(order).signum() != 0) {
            return false;
        }
        orderService.updateOrderStatus(orderId, OrderStatus.PAID);
        return true;
    }
}
