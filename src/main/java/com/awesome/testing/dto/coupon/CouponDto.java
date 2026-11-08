package com.awesome.testing.dto.coupon;

import com.awesome.testing.entity.coupon.CouponType;
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
@Schema(description = "Coupon")
public class CouponDto {
    @Schema(description = "Coupon ID", example = "1")
    private Long id;
    @Schema(description = "Upper-case coupon code", example = "WELCOME10")
    private String code;
    @Schema(types = {"string", "null"}, description = "Internal description")
    private String description;
    @Schema(description = "Discount type", example = "PERCENTAGE")
    private CouponType type;
    @Schema(description = "Percent or fixed amount", example = "10.00")
    private BigDecimal value;
    @Schema(types = {"number", "null"}, description = "Minimum cart subtotal")
    private BigDecimal minOrderAmount;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "Start of validity")
    private LocalDateTime validFrom;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "End of validity")
    private LocalDateTime validUntil;
    @Schema(types = {"integer", "null"}, description = "Global redemption limit")
    private Integer maxRedemptions;
    @Schema(description = "Redemptions allowed per user", example = "1")
    private Integer perUserLimit;
    @Schema(description = "Redemptions currently counted", example = "0")
    private Integer redemptionCount;
    @Schema(description = "Administrator switch", example = "true")
    private Boolean active;
    @Schema(description = "Effective status at the time of the request", example = "ACTIVE")
    private CouponStatus status;
    @Schema(type = "string", format = "local-date-time", description = "Creation timestamp")
    private LocalDateTime createdAt;
    @Schema(type = "string", format = "local-date-time", description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
