package com.awesome.testing.controller;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrator order operations that are not part of the course's order API. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "Orders", description = "Order management endpoints")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
@ApiResponse(responseCode = "403", description = "Forbidden",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class AdminOrderController {

    private final OrderService orderService;

    @PostMapping("/{id}/expire-reservation")
    @Operation(summary = "Expire an order's stock reservation now (Admin only)",
            description = "Time control for tests and the distributed-systems lab: sets the reservation end of a PENDING "
                    + "order to now and expires it at once, exactly as the reservation expiry job would. The order is "
                    + "cancelled, its stock is restored (ORDER_RESTORED, actor system), its coupon, gift card and points "
                    + "are released, and order.cancelled (reason RESERVATION_EXPIRED) and order.reservation-expired "
                    + "are published. Works for orders created without a reservation too.")
    @ApiResponse(responseCode = "200", description = "Reservation expired; the cancelled order is returned")
    @ApiResponse(responseCode = "404", description = "Order not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "The order is not PENDING, for example \"Only PENDING orders hold a reservation; order is PAID\"",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public OrderDto expireReservation(@Parameter(description = "Order id") @PathVariable Long id) {
        return orderService.expireReservationNow(id);
    }
}
