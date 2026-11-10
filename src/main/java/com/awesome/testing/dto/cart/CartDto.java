package com.awesome.testing.dto.cart;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"string", "null"}, description = "Coupon applied to the cart", example = "WELCOME10")
    private String couponCode;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"boolean", "null"}, description = "Whether the applied coupon currently qualifies; null when no coupon is applied")
    private Boolean couponValid;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"string", "null"}, description = "Why the applied coupon does not qualify", example = "Order subtotal must be at least 100.00")
    private String couponMessage;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Discount that checkout would apply", example = "19.96")
    private BigDecimal discountAmount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Total price after the discount", example = "179.60")
    private BigDecimal totalAfterDiscount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"string", "null"}, description = "Gift card applied to the cart; omitted when none is applied", example = "GC-7KQ2-M9XD-4HPA")
    private String giftCardCode;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"number", "null"}, description = "Part of the total checkout would pay with the gift card (zero when the card cannot be used)", example = "25.00")
    private BigDecimal giftCardAmount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"integer", "null"}, description = "Loyalty points checkout would redeem, capped at what the remaining total needs", example = "500")
    private Long pointsRedeemed;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"number", "null"}, description = "Value of the redeemed points (100 points = 1.00)", example = "5.00")
    private BigDecimal pointsAmount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"number", "null"}, description = "What remains to pay after the coupon, gift card and points; only present when a gift card or points are applied", example = "149.60")
    private BigDecimal amountDue;
} 