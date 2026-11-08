package com.awesome.testing.dto.fulfilment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Release a PAID order to the warehouse")
public class CreateShipmentDto {
    @NotNull(message = "Order ID is required")
    @Schema(description = "Order ID", example = "42")
    private Long orderId;
}
