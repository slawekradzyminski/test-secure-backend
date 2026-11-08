package com.awesome.testing.dto.fulfilment;

import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.entity.fulfilment.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Pick list grouped by product and sorted by category, then name")
public class PickListDto {
    @Schema(description = "Shipment ID", example = "1")
    private Long shipmentId;
    @Schema(description = "Order ID", example = "42")
    private Long orderId;
    @Schema(description = "Shipment status", example = "AWAITING_PICK")
    private ShipmentStatus status;
    @Schema(description = "Units to pick", example = "3")
    private int totalUnits;
    @Schema(description = "Shipping address")
    private AddressDto shippingAddress;
    @Schema(description = "Products to pick")
    private List<PickListItemDto> items;
}
