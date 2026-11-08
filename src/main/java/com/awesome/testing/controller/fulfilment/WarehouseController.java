package com.awesome.testing.controller.fulfilment;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.fulfilment.CreateShipmentDto;
import com.awesome.testing.dto.fulfilment.PackShipmentDto;
import com.awesome.testing.dto.fulfilment.PickListDto;
import com.awesome.testing.dto.fulfilment.ShipShipmentDto;
import com.awesome.testing.dto.fulfilment.ShipmentDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.entity.fulfilment.ShipmentStatus;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.fulfilment.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ROLE_WAREHOUSE', 'ROLE_ADMIN')")
@RequestMapping("/api/v1/warehouse")
@Tag(name = "Warehouse", description = "Fulfilment queue and shipment state machine for warehouse operators")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
@ApiResponse(responseCode = "403", description = "Forbidden; requires ROLE_WAREHOUSE or ROLE_ADMIN",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class WarehouseController {

    private static final String TRANSITION_CONFLICT = "Transition not allowed from the current shipment status";

    private final ShipmentService shipmentService;

    @GetMapping("/queue")
    @Operation(summary = "Get fulfilment queue", description = "Lists PAID orders that have not been released to the warehouse yet, oldest first. Negative page is clamped to zero; size is clamped to 1-100.")
    @ApiResponse(responseCode = "200", description = "Queue returned")
    public PageDto<OrderDto> queue(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1-100") @RequestParam(defaultValue = "20") int size) {
        return PageDto.from(shipmentService.queue(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))));
    }

    @PostMapping("/shipments")
    @Operation(summary = "Create shipment", description = "Releases a PAID order to the warehouse as an AWAITING_PICK shipment.")
    @ApiResponse(responseCode = "201", description = "Shipment created")
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsDto.class)))
    @ApiResponse(responseCode = "404", description = "Order not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Order is not PAID or already has a shipment",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<ShipmentDto> create(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody CreateShipmentDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shipmentService.create(request.getOrderId(), principal.getUsername()));
    }

    @GetMapping("/shipments")
    @Operation(summary = "List shipments", description = "Lists shipments oldest first, optionally filtered by status. Negative page is clamped to zero; size is clamped to 1-100.")
    @ApiResponse(responseCode = "200", description = "Shipments returned")
    public PageDto<ShipmentDto> list(
            @RequestParam(required = false) ShipmentStatus status,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1-100") @RequestParam(defaultValue = "20") int size) {
        return PageDto.from(shipmentService.list(status, PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")))));
    }

    @GetMapping("/shipments/{id}")
    @Operation(summary = "Get shipment", description = "Returns one shipment with its timestamps.")
    @ApiResponse(responseCode = "200", description = "Shipment returned")
    @ApiResponse(responseCode = "404", description = "Shipment not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ShipmentDto get(@PathVariable Long id) {
        return shipmentService.get(id);
    }

    @GetMapping("/shipments/{id}/pick-list")
    @Operation(summary = "Get pick list", description = "Returns the products to pick grouped by product and sorted by category, then name.")
    @ApiResponse(responseCode = "200", description = "Pick list returned")
    @ApiResponse(responseCode = "404", description = "Shipment not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public PickListDto pickList(@PathVariable Long id) {
        return shipmentService.pickList(id);
    }

    @PostMapping("/shipments/{id}/pick")
    @Operation(summary = "Mark shipment picked", description = "AWAITING_PICK to PICKED.")
    @ApiResponse(responseCode = "200", description = "Shipment picked")
    @ApiResponse(responseCode = "404", description = "Shipment not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = TRANSITION_CONFLICT,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ShipmentDto pick(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long id) {
        return shipmentService.pick(id, principal.getUsername());
    }

    @PostMapping("/shipments/{id}/pack")
    @Operation(summary = "Mark shipment packed", description = "PICKED to PACKED with the package weight (0.01-30.00 kg).")
    @ApiResponse(responseCode = "200", description = "Shipment packed")
    @ApiResponse(responseCode = "400", description = "Invalid weight",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsDto.class)))
    @ApiResponse(responseCode = "404", description = "Shipment not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = TRANSITION_CONFLICT,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ShipmentDto pack(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody PackShipmentDto request) {
        return shipmentService.pack(id, request.getWeightKg(), principal.getUsername());
    }

    @PostMapping("/shipments/{id}/ship")
    @Operation(summary = "Hand shipment to carrier", description = "PACKED to SHIPPED. The tracking number must match the carrier format. The order becomes SHIPPED and the customer receives an email.")
    @ApiResponse(responseCode = "200", description = "Shipment shipped")
    @ApiResponse(responseCode = "400", description = "Missing carrier or invalid tracking number",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "404", description = "Shipment not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = TRANSITION_CONFLICT,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ShipmentDto ship(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ShipShipmentDto request) {
        return shipmentService.ship(id, request.getCarrier(), request.getTrackingNumber(), principal.getUsername());
    }

    @PostMapping("/shipments/{id}/deliver")
    @Operation(summary = "Confirm delivery", description = "SHIPPED to DELIVERED. The order becomes DELIVERED, which enables reviews and returns, and the customer receives an email.")
    @ApiResponse(responseCode = "200", description = "Shipment delivered")
    @ApiResponse(responseCode = "404", description = "Shipment not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = TRANSITION_CONFLICT,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ShipmentDto deliver(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long id) {
        return shipmentService.deliver(id, principal.getUsername());
    }

    @PostMapping("/shipments/{id}/cancel")
    @Operation(summary = "Cancel shipment", description = "Cancels an AWAITING_PICK or PICKED shipment. The order is cancelled, stock is restored and any coupon redemption is released.")
    @ApiResponse(responseCode = "200", description = "Shipment cancelled")
    @ApiResponse(responseCode = "404", description = "Shipment not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = TRANSITION_CONFLICT,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ShipmentDto cancel(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long id) {
        return shipmentService.cancel(id, principal.getUsername());
    }
}
