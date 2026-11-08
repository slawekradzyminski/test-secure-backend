package com.awesome.testing.controller.returns;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.returns.ReceiveReturnDto;
import com.awesome.testing.dto.returns.RejectReturnDto;
import com.awesome.testing.dto.returns.ReturnDto;
import com.awesome.testing.entity.returns.ReturnStatus;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.returns.ReturnService;
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
@RequestMapping("/api/v1/admin/returns")
@Tag(name = "Returns", description = "Customer returns (RMA)")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
@ApiResponse(responseCode = "403", description = "Forbidden",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class AdminReturnController {

    private static final String NOT_FOUND = "Return not found";
    private static final String CONFLICT = "Transition not allowed from the current return status";

    private final ReturnService returnService;

    @GetMapping
    @Operation(summary = "List returns", description = "Lists returns oldest first, optionally filtered by status. Negative page is clamped to zero; size is clamped to 1-100.")
    @ApiResponse(responseCode = "200", description = "Returns listed")
    public PageDto<ReturnDto> list(
            @RequestParam(required = false) ReturnStatus status,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1-100") @RequestParam(defaultValue = "20") int size) {
        return PageDto.from(returnService.list(status, PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get return", description = "Returns any customer's return.")
    @ApiResponse(responseCode = "200", description = "Return found")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ReturnDto get(@PathVariable Long id) {
        return returnService.get(id);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve return", description = "REQUESTED to APPROVED. The customer is emailed.")
    @ApiResponse(responseCode = "200", description = "Return approved")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = CONFLICT,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ReturnDto approve(@PathVariable Long id) {
        return returnService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject return", description = "REQUESTED to REJECTED with a note for the customer. Rejected quantities become returnable again.")
    @ApiResponse(responseCode = "200", description = "Return rejected")
    @ApiResponse(responseCode = "400", description = "Missing note",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsDto.class)))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = CONFLICT,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ReturnDto reject(@PathVariable Long id, @Valid @RequestBody RejectReturnDto request) {
        return returnService.reject(id, request.getNote());
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "Receive returned goods", description = "APPROVED to RECEIVED. Every returned product needs a condition; RESELLABLE units go back to stock as RETURN_RESTOCKED movements.")
    @ApiResponse(responseCode = "200", description = "Return received")
    @ApiResponse(responseCode = "400", description = "Conditions missing or not matching the returned products",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = CONFLICT,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ReturnDto receive(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ReceiveReturnDto request) {
        return returnService.receive(id, request.getItems(), principal.getUsername());
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund return", description = "RECEIVED to REFUNDED. The refund is the returned items' value scaled by the order's total-to-subtotal ratio, rounded half-up. The customer is emailed.")
    @ApiResponse(responseCode = "200", description = "Return refunded")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = CONFLICT,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ReturnDto refund(@PathVariable Long id) {
        return returnService.refund(id);
    }
}
