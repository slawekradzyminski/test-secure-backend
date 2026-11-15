package com.awesome.testing.dto.stock;

import java.util.List;
import java.util.UUID;

/** warehouse-service's reply to a reserve or confirm. */
public record ReservationDto(UUID reservationId, List<ReservedLineDto> lines) {
}
