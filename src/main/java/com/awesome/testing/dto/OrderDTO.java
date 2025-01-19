package com.awesome.testing.dto;

import com.awesome.testing.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Order data transfer object")
public class OrderDTO {
    @Schema(description = "Order ID", example = "1")
    private Long id;

    @Schema(description = "Username", example = "john.doe")
    private String username;

    @Builder.Default
    @Valid
    @Schema(description = "Order items")
    private List<OrderItemDTO> items = new ArrayList<>();

    @Schema(description = "Total amount", example = "1999.98")
    private BigDecimal totalAmount;

    @Schema(description = "Order status", example = "PENDING")
    private OrderStatus status;

    @Valid
    @NotNull(message = "Shipping address is required")
    @Schema(description = "Shipping address")
    private AddressDTO shippingAddress;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
} 