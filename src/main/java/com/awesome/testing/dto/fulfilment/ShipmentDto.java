package com.awesome.testing.dto.fulfilment;

import com.awesome.testing.entity.fulfilment.Carrier;
import com.awesome.testing.entity.fulfilment.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Warehouse shipment for one order")
public class ShipmentDto {
    @Schema(description = "Shipment ID", example = "1")
    private Long id;
    @Schema(description = "Order ID", example = "42")
    private Long orderId;
    @Schema(description = "Order owner", example = "client")
    private String username;
    @Schema(description = "Shipment status", example = "AWAITING_PICK")
    private ShipmentStatus status;
    @Schema(description = "Units to pick", example = "3")
    private int totalUnits;
    @Schema(types = {"string", "null"}, description = "Carrier, set when shipped", example = "DHL")
    private Carrier carrier;
    @Schema(types = {"string", "null"}, description = "Carrier tracking number", example = "1234567890")
    private String trackingNumber;
    @Schema(types = {"number", "null"}, description = "Package weight in kilograms, set when packed", example = "1.25")
    private BigDecimal weightKg;
    @Schema(description = "User who released the order to the warehouse", example = "warehouse")
    private String createdBy;
    @Schema(description = "User who performed the latest transition", example = "warehouse")
    private String lastActor;
    @Schema(type = "string", format = "local-date-time", description = "Creation timestamp")
    private LocalDateTime createdAt;
    @Schema(type = "string", format = "local-date-time", description = "Last update timestamp")
    private LocalDateTime updatedAt;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "When picking finished")
    private LocalDateTime pickedAt;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "When packing finished")
    private LocalDateTime packedAt;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "When the carrier collected the parcel")
    private LocalDateTime shippedAt;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "When the parcel was delivered")
    private LocalDateTime deliveredAt;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "When the shipment was cancelled")
    private LocalDateTime cancelledAt;
}
