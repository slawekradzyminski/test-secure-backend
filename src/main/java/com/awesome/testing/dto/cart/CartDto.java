package com.awesome.testing.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartDto {
    @Schema(description = "Cart owner", example = "admin")

    private String username;

    private List<CartItemDto> items;

    @Schema(description = "Total Cart price", example = "199.56")
    private BigDecimal totalPrice;

    @Schema(description = "Total number of items", example = "7")
    private int totalItems;
} 