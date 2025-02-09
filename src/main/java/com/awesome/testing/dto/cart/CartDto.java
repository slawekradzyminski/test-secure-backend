package com.awesome.testing.dto.cart;

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
    private String username;
    private List<CartItemDto> items;
    private BigDecimal totalPrice;
    private int totalItems;
} 