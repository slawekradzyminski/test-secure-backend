package com.awesome.testing.dto.order;

import com.awesome.testing.entity.OrderEntity;
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

    /** Omitted for orders the warehouse never touched, keeping the course's order bodies unchanged. */
    private static FulfilmentState warehouseState(OrderEntity order) {
        FulfilmentState state = FulfilmentState.orNone(order.getFulfilmentState());
        return state == FulfilmentState.NONE ? null : state;
    }
}
