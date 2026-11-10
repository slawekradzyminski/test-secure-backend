package com.awesome.testing.dto.loyalty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Loyalty account of the signed-in user. 1 point is earned per full 1.00 of a paid order; 100 points redeem 1.00.")
public class LoyaltyAccountDto {

    @Schema(description = "Points available to redeem", example = "1250")
    private long balance;

    @Schema(description = "Points earned over the account's lifetime", example = "1800")
    private long lifetimeEarned;

    @Schema(description = "Tier from lifetime points: BRONZE, SILVER from 1000, GOLD from 5000", example = "SILVER")
    private LoyaltyTier tier;

    @Schema(description = "What the balance is worth at checkout (100 points = 1.00)", example = "12.50")
    private BigDecimal valueOfBalance;
}
