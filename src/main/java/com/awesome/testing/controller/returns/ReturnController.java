package com.awesome.testing.controller.returns;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.returns.CreateReturnDto;
import com.awesome.testing.dto.returns.ReturnDto;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Returns", description = "Customer returns (RMA)")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class ReturnController {

    private final ReturnService returnService;

    @PostMapping("/api/v1/orders/{orderId}/returns")
    @Operation(summary = "Request a return", description = "Opens a return for the caller's DELIVERED order within 30 days of delivery. Each product may appear once, and quantities cannot exceed what was bought minus units in other non-rejected returns.")
    @ApiResponse(responseCode = "201", description = "Return requested")
    @ApiResponse(responseCode = "400", description = "Invalid request, duplicate product, or product not in the order",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "404", description = "Order not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Order not delivered, return window passed, or quantity exceeds what can be returned",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<ReturnDto> create(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long orderId,
            @Valid @RequestBody CreateReturnDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(returnService.create(principal.getUsername(), orderId, request));
    }

    @GetMapping("/api/v1/returns")
    @Operation(summary = "List my returns", description = "Lists the caller's returns newest first. Negative page is clamped to zero; size is clamped to 1-100.")
    @ApiResponse(responseCode = "200", description = "Returns listed")
    public PageDto<ReturnDto> mine(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1-100") @RequestParam(defaultValue = "10") int size) {
        return PageDto.from(returnService.mine(principal.getUsername(), PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))));
    }

    @GetMapping("/api/v1/returns/{id}")
    @Operation(summary = "Get my return", description = "Returns one of the caller's returns with its estimated or paid refund.")
    @ApiResponse(responseCode = "200", description = "Return found")
    @ApiResponse(responseCode = "404", description = "Return not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ReturnDto get(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable Long id) {
        return returnService.getMine(principal.getUsername(), id);
    }
}
