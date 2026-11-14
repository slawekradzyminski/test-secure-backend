package com.awesome.testing.dto.stock;

/** {@code POST /internal/v1/stock/reservations/{id}/confirm}. */
public record ConfirmReservationRequestDto(Long orderId) {
}
