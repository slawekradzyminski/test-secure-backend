package com.awesome.testing.internal.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.FulfilmentState;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.internal.dto.InternalOrderDto;
import com.awesome.testing.internal.dto.OrderReferenceDto;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.stock.StockReservationClaimRepository;
import com.awesome.testing.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Order reads and fulfilment writes for warehouse-service; the business rules live in {@link OrderService}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalOrderService {

    static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final StockReservationClaimRepository reservationClaims;
    private final Clock commerceClock;

    @Transactional(readOnly = true)
    public InternalOrderDto get(Long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .map(InternalOrderDto::from)
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));
    }

    /**
     * The order a reservation belongs to, for warehouse-service's reaper. First a tombstone is
     * committed in {@code stock_reservation_claims} (V26): the insert waits for a checkout that is
     * still saving an order for this reservation, and once committed it stops any later checkout from
     * storing one. Only then is the order looked up, so a 404 is final: the reaper may release.
     * When an order exists, the tombstone is removed again.
     *
     * <p>Deliberately not transactional: each repository call commits on its own, in this order.
     */
    public OrderReferenceDto byReservation(UUID reservationId) {
        try {
            reservationClaims.insert(reservationId, commerceClock.instant());
        } catch (DataIntegrityViolationException alreadyTombstoned) {
            // The order lookup below still decides.
            log.debug("Reservation {} already has a tombstone from an earlier reaper run", reservationId);
        }
        Optional<Long> orderId = orderRepository.findIdByStockReservationId(reservationId);
        if (orderId.isEmpty()) {
            throw new CustomException("Order not found", HttpStatus.NOT_FOUND);
        }
        reservationClaims.deleteClaim(reservationId);
        return new OrderReferenceDto(orderId.get());
    }

    @Transactional(readOnly = true)
    public PageDto<InternalOrderDto> find(OrderStatus status, FulfilmentState fulfilment, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        return PageDto.from(orderRepository.findByStatusAndFulfilmentStateOldestFirst(status, fulfilment, pageable)
                .map(InternalOrderDto::from));
    }

    @Transactional(readOnly = true)
    public List<InternalOrderDto> findByIds(List<Long> ids) {
        if (ids.size() > MAX_PAGE_SIZE) {
            throw new CustomException("At most " + MAX_PAGE_SIZE + " ids are allowed", HttpStatus.BAD_REQUEST);
        }
        return orderRepository.findWithItemsByIdIn(ids).stream()
                .sorted(java.util.Comparator.comparing(com.awesome.testing.entity.OrderEntity::getId))
                .map(InternalOrderDto::from)
                .toList();
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
