package com.awesome.testing.dto.order;

import com.awesome.testing.entity.OrderEntity;
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
    @Valid
    @Schema(description = "Order items")
    private List<OrderItemDto> items = new ArrayList<>();

    @Schema(description = "Total amount", example = "1999.98")
    private BigDecimal totalAmount;

    @Schema(description = "Order status", example = "PENDING")
    private OrderStatus status;

    @Valid
    @NotNull(message = "Shipping address is required")
    @Schema(description = "Shipping address")
    private AddressDto shippingAddress;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

    public static OrderDto from(OrderEntity order) {
        return OrderDto.builder()
                .id(order.getId())
                .username(order.getUsername())
                .items(order.getItems().stream().map(OrderItemDto::from).toList())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .shippingAddress(AddressDto.from(order.getShippingAddress()))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
} 