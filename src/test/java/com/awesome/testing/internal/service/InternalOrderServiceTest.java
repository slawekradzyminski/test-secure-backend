package com.awesome.testing.internal.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.FulfilmentState;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.AddressEntity;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalOrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @InjectMocks private InternalOrderService service;

    @Test
    void pageSizeIsClampedBetweenOneAndOneHundredAndPageToZero() {
        assertThat(InternalOrderService.clampSize(0)).isEqualTo(1);
        assertThat(InternalOrderService.clampSize(-5)).isEqualTo(1);
        assertThat(InternalOrderService.clampSize(1)).isEqualTo(1);
        assertThat(InternalOrderService.clampSize(37)).isEqualTo(37);
        assertThat(InternalOrderService.clampSize(100)).isEqualTo(100);
        assertThat(InternalOrderService.clampSize(101)).isEqualTo(100);

        PageRequest expected = PageRequest.of(0, 100);
        when(orderRepository.findByStatusAndFulfilmentStateOldestFirst(OrderStatus.PAID, FulfilmentState.NONE, expected))
                .thenReturn(new PageImpl<>(List.of(order()), expected, 1));

        var page = service.find(OrderStatus.PAID, FulfilmentState.NONE, -2, 500);

        assertThat(page.getContent()).extracting(dto -> dto.id()).containsExactly(7L);
        assertThat(page.getPageSize()).isEqualTo(100);
    }

    @Test
    void snapshotsAndWritesAreMappedFromTheOrder() {
        when(orderRepository.findWithItemsById(7L)).thenReturn(Optional.of(order()));
        when(orderService.applyFulfilmentState(7L, FulfilmentState.PICKED)).thenReturn(order());
        when(orderService.cancelForWarehouse(7L)).thenReturn(order());

        assertThat(service.get(7L).fulfilmentState()).isEqualTo(FulfilmentState.AWAITING_PICK);
        assertThat(service.applyFulfilment(7L, FulfilmentState.PICKED).id()).isEqualTo(7L);
        assertThat(service.warehouseCancel(7L).username()).isEqualTo("buyer");
    }

    @Test
    void unknownOrdersAreNotFound() {
        when(orderRepository.findWithItemsById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(8L))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static OrderEntity order() {
        return OrderEntity.builder().id(7L).username("buyer").status(OrderStatus.PAID)
                .fulfilmentState(FulfilmentState.AWAITING_PICK).totalAmount(BigDecimal.TEN)
                .shippingAddress(new AddressEntity()).build();
    }
}
