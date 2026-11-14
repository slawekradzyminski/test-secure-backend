package com.awesome.testing.dto.order;

import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.service.checkout.OrderAmounts;
import com.fasterxml.jackson.annotation.JsonInclude;
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
public class OrderDto {
    @Schema(description = "Order ID", example = "1")
    private Long id;

    @Schema(description = "Username", example = "john.doe")
    private String username;

    @Builder.Default
    @Schema(description = "Order items")
    private List<@Valid OrderItemDto> items = new ArrayList<>();

    @Schema(description = "Total amount", example = "1999.98")
    private BigDecimal totalAmount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Sum of item prices before discounts", example = "2222.20")
    private BigDecimal subtotalAmount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Coupon discount subtracted from the subtotal", example = "222.22")
    private BigDecimal discountAmount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"string", "null"}, description = "Coupon code applied at checkout", example = "WELCOME10")
    private String couponCode;

    @Schema(description = "Order status", example = "PENDING")
    private OrderStatus status;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"string", "null"}, description = "Warehouse progress mirrored from warehouse-service; omitted until the order is released to the warehouse",
            example = "AWAITING_PICK")
    private FulfilmentState fulfilmentState;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"number", "null"}, description = "Amount refunded for returned items; omitted until a refund is booked",
            example = "49.99")
    private BigDecimal refundedAmount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"string", "null"}, description = "ISO country code whose VAT is contained in the total; omitted when no VAT rate applies", example = "PL")
    private String taxCountry;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"number", "null"}, description = "VAT rate in percent at checkout; 0 means VAT is not applicable", example = "23.00")
    private BigDecimal taxRate;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"number", "null"}, description = "VAT contained in the VAT-inclusive total", example = "373.98")
    private BigDecimal taxAmount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"number", "null"}, description = "Total without VAT (totalAmount - taxAmount)", example = "1626.00")
    private BigDecimal netAmount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"string", "null"}, description = "Gift card used at checkout; omitted when none was used", example = "GC-7KQ2-M9XD-4HPA")
    private String giftCardCode;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"number", "null"}, description = "Part of the total paid with the gift card", example = "25.00")
    private BigDecimal giftCardAmount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"integer", "null"}, description = "Loyalty points redeemed at checkout", example = "500")
    private Long pointsRedeemed;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"number", "null"}, description = "Part of the total paid with loyalty points (100 points = 1.00)", example = "5.00")
    private BigDecimal pointsAmount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"number", "null"}, description = "Total minus the gift card and points parts, which a card payment must cover; only present when a gift card or points were used",
            example = "1969.98")
    private BigDecimal amountDue;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(types = {"string", "null"}, format = "date-time",
            description = "When the stock reservation of a PENDING order ends (ISO-8601 UTC, milliseconds); the order "
                    + "is cancelled if it is not paid by then. Omitted for orders created without a reservation",
            example = "2026-11-14T10:30:00.000Z")
    private String reservationExpiresAt;

    @Valid
    @NotNull(message = "Shipping address is required")
    @Schema(description = "Shipping address")
    private AddressDto shippingAddress;

    @Schema(type = "string", format = "local-date-time", description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(type = "string", format = "local-date-time", description = "Last update timestamp")
    private LocalDateTime updatedAt;

    public static OrderDto from(OrderEntity order) {
        return OrderDto.builder()
                .id(order.getId())
                .username(order.getUsername())
                .items(order.getItems().stream().map(OrderItemDto::from).toList())
                .totalAmount(order.getTotalAmount())
                .subtotalAmount(hasCoupon(order) ? subtotalOf(order) : null)
                .discountAmount(hasCoupon(order) ? order.getDiscountAmount() : null)
                .couponCode(order.getCouponCode())
                .status(order.getStatus())
                .fulfilmentState(warehouseState(order))
                .refundedAmount(refundedAmountOf(order))
                .taxCountry(order.getTaxCountry())
                .taxRate(order.getTaxRate())
                .taxAmount(order.getTaxAmount())
                .netAmount(order.getNetAmount())
                .giftCardCode(order.getGiftCardCode())
                .giftCardAmount(order.getGiftCardAmount())
                .pointsRedeemed(order.getPointsRedeemed())
                .pointsAmount(order.getPointsAmount())
                .amountDue(OrderAmounts.hasTenders(order) ? OrderAmounts.amountDue(order) : null)
                .reservationExpiresAt(UtcMillis.format(order.getReservationExpiresAt()))
                .shippingAddress(AddressDto.from(order.getShippingAddress()))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    /**
     * Coupon fields are sent only for discounted orders, so orders without a
     * coupon serialize exactly as before coupons existed (course contract).
     */
    private static boolean hasCoupon(OrderEntity order) {
        return order.getCouponCode() != null
                || (order.getDiscountAmount() != null && order.getDiscountAmount().signum() > 0);
    }

    private static BigDecimal subtotalOf(OrderEntity order) {
        return order.getSubtotalAmount() == null ? order.getTotalAmount() : order.getSubtotalAmount();
    }

    /** Omitted until a refund exists, keeping the course's order bodies unchanged. */
    private static BigDecimal refundedAmountOf(OrderEntity order) {
        return order.getRefundedAmount() == null || order.getRefundedAmount().signum() == 0
                ? null : order.getRefundedAmount();
    }

    /** Omitted for orders the warehouse never touched, keeping the course's order bodies unchanged. */
    private static FulfilmentState warehouseState(OrderEntity order) {
        FulfilmentState state = FulfilmentState.orNone(order.getFulfilmentState());
        return state == FulfilmentState.NONE ? null : state;
    }
}
