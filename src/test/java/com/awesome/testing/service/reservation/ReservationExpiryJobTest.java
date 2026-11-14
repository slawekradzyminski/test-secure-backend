package com.awesome.testing.service.reservation;

import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationExpiryJobTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00.123456Z");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderService orderService;

    private ReservationExpiryJob job(boolean enabled) {
        return new ReservationExpiryJob(orderRepository, orderService, Clock.fixed(NOW, ZoneOffset.UTC), enabled);
    }

    @Test
    void expiresOneBatchOfFiftyDuePendingOrdersEarliestFirstAndCountsOnlyTheOnesItCancelled() {
        when(orderRepository.findExpiredReservationIds(OrderStatus.PENDING, NOW, PageRequest.of(0, 50)))
                .thenReturn(List.of(3L, 1L, 2L));
        when(orderService.expireIfDue(3L)).thenReturn(true);
        when(orderService.expireIfDue(1L)).thenReturn(false);
        when(orderService.expireIfDue(2L)).thenReturn(true);

        int expired = job(true).expireDue();

        assertThat(expired).isEqualTo(2);
        var order = inOrder(orderService);
        order.verify(orderService).expireIfDue(3L);
        order.verify(orderService).expireIfDue(1L);
        order.verify(orderService).expireIfDue(2L);
    }

    @Test
    void anOrderThatFailsDoesNotStopTheRestOfTheBatch() {
        when(orderRepository.findExpiredReservationIds(any(), any(), any())).thenReturn(List.of(1L, 2L));
        when(orderService.expireIfDue(1L)).thenThrow(new IllegalStateException("product gone"));
        when(orderService.expireIfDue(2L)).thenReturn(true);

        assertThat(job(true).expireDue()).isEqualTo(1);
        verify(orderService).expireIfDue(2L);
    }

    @Test
    void nothingDueExpiresNothing() {
        when(orderRepository.findExpiredReservationIds(any(), any(), any())).thenReturn(List.of());

        assertThat(job(true).expireDue()).isZero();
        verifyNoInteractions(orderService);
    }

    @Test
    void theScheduleRunsTheBatchOnlyWhenEnabled() {
        when(orderRepository.findExpiredReservationIds(any(), any(), any())).thenReturn(List.of(5L));
        when(orderService.expireIfDue(5L)).thenReturn(true);

        job(false).scheduledExpiry();
        verifyNoInteractions(orderRepository, orderService);

        job(true).scheduledExpiry();
        verify(orderService).expireIfDue(5L);
    }

    @Test
    void theBatchSizeIsFifty() {
        assertThat(ReservationExpiryJob.BATCH_SIZE).isEqualTo(50);
    }
}
