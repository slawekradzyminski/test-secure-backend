package com.awesome.testing.internal.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.FulfilmentState;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.internal.dto.InternalOrderDto;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Order reads and fulfilment writes for warehouse-service; the business rules live in {@link OrderService}. */
@Service
@RequiredArgsConstructor
public class InternalOrderService {

    static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Transactional(readOnly = true)
    public InternalOrderDto get(Long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .map(InternalOrderDto::from)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PageDto<InternalOrderDto> find(OrderStatus status, FulfilmentState fulfilment, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        return PageDto.from(orderRepository.findByStatusAndFulfilmentStateOldestFirst(status, fulfilment, pageable)
                .map(InternalOrderDto::from));
    }

    @Transactional
    public InternalOrderDto applyFulfilment(Long orderId, FulfilmentState state) {
        return InternalOrderDto.from(orderService.applyFulfilmentState(orderId, state));
    }

    @Transactional
    public InternalOrderDto warehouseCancel(Long orderId) {
        return InternalOrderDto.from(orderService.cancelForWarehouse(orderId));
    }

    static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
