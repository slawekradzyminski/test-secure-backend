package com.awesome.testing.controller;

import com.awesome.testing.dto.inventory.InventoryAdjustmentDto;
import com.awesome.testing.dto.inventory.InventoryItemDto;
import com.awesome.testing.dto.inventory.InventoryMovementDto;
import com.awesome.testing.dto.inventory.StockStatus;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequestMapping("/api/v1/admin/inventory")
@Tag(name = "Inventory", description = "Administrator inventory management endpoints")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized")
@ApiResponse(responseCode = "403", description = "Forbidden")
public class AdminInventoryController {
    private final InventoryService service;

    @GetMapping
    @Operation(summary = "List inventory", description = "Lists inventory with optional stock and catalog filters.")
    @ApiResponse(responseCode = "200", description = "Inventory returned")
    public ResponseEntity<PageDto<InventoryItemDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) StockStatus status,
            @RequestParam(defaultValue = "5") int lowStockThreshold) {
        return ResponseEntity.ok(PageDto.from(service.list(
                page, size, search, category, status, lowStockThreshold)));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get inventory item", description = "Returns the available quantity for one product.")
    @ApiResponse(responseCode = "200", description = "Inventory item returned")
    public InventoryItemDto get(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "5") int lowStockThreshold) {
        return service.get(productId, lowStockThreshold);
    }

    @PostMapping("/{productId}/adjustments")
    @Operation(summary = "Adjust inventory", description = "Applies an idempotent administrator inventory adjustment.")
    @ApiResponse(responseCode = "201", description = "Adjustment recorded")
    public ResponseEntity<InventoryMovementDto> adjust(
            @PathVariable Long productId,
            @Valid @RequestBody InventoryAdjustmentDto request,
            @AuthenticationPrincipal CustomPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.adjust(productId, request, principal.getUsername()));
    }

    @GetMapping("/{productId}/movements")
    @Operation(summary = "List inventory movements", description = "Lists an inventory item's movement history newest first.")
    @ApiResponse(responseCode = "200", description = "Movement history returned")
    public PageDto<InventoryMovementDto> movements(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageDto.from(service.movements(
                productId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))));
    }
}
