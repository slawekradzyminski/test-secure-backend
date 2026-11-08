package com.awesome.testing.dto.fulfilment;

import com.awesome.testing.entity.fulfilment.Carrier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Carrier hand-over. INPOST: 24 digits, DPD: 14 digits, DHL: 10 digits, UPS: 1Z + 16 letters or digits.")
public class ShipShipmentDto {
    @NotNull(message = "Carrier is required")
    @Schema(description = "Carrier", example = "DHL")
    private Carrier carrier;

    @NotBlank(message = "Tracking number is required")
    @Schema(minLength = 1, pattern = "\\S", description = "Tracking number; surrounding spaces are ignored and letters are upper-cased", example = "1234567890")
    private String trackingNumber;
}
