package com.awesome.testing.controller.purchasing;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.purchasing.PurchaseOrderDto;
import com.awesome.testing.dto.purchasing.PurchaseOrderRequestDto;
import com.awesome.testing.dto.purchasing.ReceiptRequestDto;
import com.awesome.testing.dto.purchasing.ReorderSuggestionDto;
import com.awesome.testing.dto.purchasing.SupplierDto;
import com.awesome.testing.dto.purchasing.SupplierRequestDto;
import com.awesome.testing.entity.purchasing.PurchaseOrderStatus;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.purchasing.PurchasingService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
@Tag(name = "Purchasing", description = "Suppliers, purchase orders, goods receipts and reorder suggestions")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
@ApiResponse(responseCode = "403", description = "Forbidden",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class PurchasingController {

    private static final String PO_PATH = "/api/v1/admin/purchase-orders";
    private static final String NOT_FOUND = "Purchase order not found";

    private final PurchasingService purchasingService;

    @GetMapping("/api/v1/admin/suppliers")
    @Operation(summary = "List suppliers", description = "Lists suppliers by name.")
    @ApiResponse(responseCode = "200", description = "Suppliers returned")
    public List<SupplierDto> suppliers() {
        return purchasingService.suppliers();
    }

    @PostMapping("/api/v1/admin/suppliers")
    @Operation(summary = "Create supplier", description = "Creates a supplier with a case-insensitively unique name and a lead time of 0-365 days.")
    @ApiResponse(responseCode = "201", description = "Supplier created")
    @ApiResponse(responseCode = "400", description = "Invalid supplier",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsDto.class)))
    @ApiResponse(responseCode = "409", description = "Supplier name already exists",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<SupplierDto> createSupplier(@Valid @RequestBody SupplierRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchasingService.createSupplier(request));
    }

    @GetMapping(PO_PATH)
    @Operation(summary = "List purchase orders", description = "Lists purchase orders newest first, optionally filtered by status. Negative page is clamped to zero; size is clamped to 1-100.")
    @ApiResponse(responseCode = "200", description = "Purchase orders returned")
    public PageDto<PurchaseOrderDto> list(
            @RequestParam(required = false) PurchaseOrderStatus status,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1-100") @RequestParam(defaultValue = "20") int size) {
        return PageDto.from(purchasingService.list(status, PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("id").descending())));
    }

    @GetMapping(PO_PATH + "/{id}")
    @Operation(summary = "Get purchase order", description = "Returns one purchase order with received and outstanding quantities.")
    @ApiResponse(responseCode = "200", description = "Purchase order returned")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public PurchaseOrderDto get(@PathVariable Long id) {
        return purchasingService.get(id);
    }

    @PostMapping(PO_PATH)
    @Operation(summary = "Create purchase order", description = "Creates a DRAFT purchase order. Lines may be empty until submission; each product may appear once.")
    @ApiResponse(responseCode = "201", description = "Draft created")
    @ApiResponse(responseCode = "400", description = "Invalid purchase order or duplicate product",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "404", description = "Supplier or product not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<PurchaseOrderDto> create(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody PurchaseOrderRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchasingService.create(request, principal.getUsername()));
    }

    @PutMapping(PO_PATH + "/{id}")
    @Operation(summary = "Update draft purchase order", description = "Replaces the supplier, note and lines of a DRAFT purchase order.")
    @ApiResponse(responseCode = "200", description = "Draft updated")
    @ApiResponse(responseCode = "400", description = "Invalid purchase order or duplicate product",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "404", description = "Purchase order, supplier or product not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Purchase order is no longer a draft",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public PurchaseOrderDto update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRequestDto request) {
        return purchasingService.update(id, request);
    }

    @PostMapping(PO_PATH + "/{id}/submit")
    @Operation(summary = "Submit purchase order", description = "DRAFT to SUBMITTED. Requires at least one line; expectedAt is the submission time plus the supplier lead time.")
    @ApiResponse(responseCode = "200", description = "Purchase order submitted")
    @ApiResponse(responseCode = "400", description = "Purchase order has no lines",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Purchase order is not a draft",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public PurchaseOrderDto submit(@PathVariable Long id) {
        return purchasingService.submit(id);
    }

    @PostMapping(PO_PATH + "/{id}/cancel")
    @Operation(summary = "Cancel purchase order", description = "Cancels a DRAFT or SUBMITTED purchase order. Orders with received goods cannot be cancelled.")
    @ApiResponse(responseCode = "200", description = "Purchase order cancelled")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Purchase order cannot be cancelled in its status",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public PurchaseOrderDto cancel(@PathVariable Long id) {
        return purchasingService.cancel(id);
    }

    @PostMapping(PO_PATH + "/{id}/receipts")
    @Operation(summary = "Receive goods", description = "Books received units for SUBMITTED or PARTIALLY_RECEIVED purchase orders and raises stock with PURCHASE_RECEIVED movements. Receiving more than is outstanding returns 409. Repeating requestId with the same lines returns the current purchase order without booking again.")
    @ApiResponse(responseCode = "200", description = "Goods received, or receipt replayed")
    @ApiResponse(responseCode = "400", description = "Invalid receipt, duplicate product, or product not on the purchase order",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Not open for receipt, over-receipt, or requestId reused with different lines",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public PurchaseOrderDto receive(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ReceiptRequestDto request) {
        return purchasingService.receive(id, request, principal.getUsername());
    }

    @GetMapping("/api/v1/admin/inventory/reorder-suggestions")
    @Operation(summary = "Get reorder suggestions", description = "Lists products whose stock plus units outstanding on open purchase orders is at or below the threshold, with suggestedQuantity = 4 × threshold − stock − incoming. Sorted by projected stock, then product id.")
    @ApiResponse(responseCode = "200", description = "Suggestions returned")
    @ApiResponse(responseCode = "400", description = "Threshold below 1",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsDto.class)))
    public List<ReorderSuggestionDto> reorderSuggestions(
            @Parameter(description = "Inclusive low-stock threshold; at least one", schema = @Schema(minimum = "1"))
            @RequestParam(defaultValue = "5") int lowStockThreshold) {
        return purchasingService.reorderSuggestions(lowStockThreshold);
    }
}
