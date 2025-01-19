package com.awesome.testing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Order item data transfer object")
public class OrderItemDTO {
    @Schema(description = "Order item ID", example = "1")
    private Long id;

    @NotNull(message = "Product ID is required")
    @Schema(description = "Product ID", example = "1")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Schema(description = "Quantity", example = "2")
    private Integer quantity;

    @Schema(description = "Product name", example = "iPhone 13 Pro")
    private String productName;

    @Schema(description = "Unit price", example = "999.99")
    private BigDecimal unitPrice;

    @Schema(description = "Total price", example = "1999.98")
    private BigDecimal totalPrice;
} 