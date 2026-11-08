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

    @Schema(types = {"string", "null"}, description = "Coupon applied to the cart", example = "WELCOME10")
    private String couponCode;

    @Schema(types = {"boolean", "null"}, description = "Whether the applied coupon currently qualifies; null when no coupon is applied")
    private Boolean couponValid;

    @Schema(types = {"string", "null"}, description = "Why the applied coupon does not qualify", example = "Order subtotal must be at least 100.00")
    private String couponMessage;

    @Schema(description = "Discount that checkout would apply", example = "19.96")
    private BigDecimal discountAmount;

    @Schema(description = "Total price after the discount", example = "179.60")
    private BigDecimal totalAfterDiscount;
} 