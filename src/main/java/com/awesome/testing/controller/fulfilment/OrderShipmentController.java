package com.awesome.testing.controller.fulfilment;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.fulfilment.ShipmentDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.fulfilment.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class OrderShipmentController {

    private final ShipmentService shipmentService;

    @GetMapping("/api/v1/orders/{id}/shipment")
    @Operation(summary = "Track order shipment", description = "Returns the shipment for one of the caller's orders. Administrators may track any order.")
    @ApiResponse(responseCode = "200", description = "Shipment returned")
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "Order not found, or not released to the warehouse yet",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ShipmentDto track(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long id) {
        boolean admin = principal.getUserDetails().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        return shipmentService.trackOrder(id, principal.getUsername(), admin);
    }
}
