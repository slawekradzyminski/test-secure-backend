package com.awesome.testing.dto.loyalty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Loyalty points to redeem at checkout, in whole multiples of 100 (100 points = 1.00)")
public class ApplyLoyaltyPointsDto {

    @NotNull(message = "Points are required")
    @Min(value = 100, message = "Points must be at least 100")
    @Max(value = 100_000_000, message = "Points cannot exceed 100000000")
    @Schema(minimum = "100", maximum = "100000000", multipleOf = 100, description = "Points to redeem", example = "500")
    private Long points;
}
