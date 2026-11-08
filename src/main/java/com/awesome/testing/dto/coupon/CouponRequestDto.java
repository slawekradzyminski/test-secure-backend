package com.awesome.testing.dto.coupon;

import com.awesome.testing.entity.coupon.CouponType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Coupon definition. PERCENTAGE values must be between 1 and 100; validUntil must be after validFrom.")
public class CouponRequestDto {

    @NotBlank(message = "Coupon code is required")
    @Pattern(regexp = "^[A-Za-z0-9-]{3,32}$", message = "Coupon code must be 3-32 letters, digits or hyphens")
    @Schema(minLength = 3, maxLength = 32, pattern = "^[A-Za-z0-9-]{3,32}$",
            description = "Case-insensitive code, stored upper-case", example = "WELCOME10")
    private String code;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    @Schema(types = {"string", "null"}, description = "Internal description", example = "Welcome discount")
    private String description;

    @NotNull(message = "Coupon type is required")
    @Schema(description = "Discount type", example = "PERCENTAGE")
    private CouponType type;

    @NotNull(message = "Coupon value is required")
    @DecimalMin(value = "0.01", message = "Coupon value must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Coupon value must have at most 8 digits and 2 decimals")
    @Schema(multipleOf = 0.01, description = "Percent (1-100) or fixed amount", example = "10")
    private BigDecimal value;

    @DecimalMin(value = "0.00", message = "Minimum order amount cannot be negative")
    @Digits(integer = 8, fraction = 2, message = "Minimum order amount must have at most 8 digits and 2 decimals")
    @Schema(types = {"number", "null"}, multipleOf = 0.01, description = "Minimum cart subtotal", example = "100.00")
    private BigDecimal minOrderAmount;

    @Schema(types = {"string", "null"}, format = "local-date-time", description = "Start of validity (server-local)")
    private LocalDateTime validFrom;

    @Schema(types = {"string", "null"}, format = "local-date-time", description = "End of validity (server-local)")
    private LocalDateTime validUntil;

    @Min(value = 1, message = "Maximum redemptions must be at least 1")
    @Schema(types = {"integer", "null"}, minimum = "1", description = "Global redemption limit; null means unlimited", example = "100")
    private Integer maxRedemptions;

    @Min(value = 1, message = "Per-user limit must be at least 1")
    @Schema(types = {"integer", "null"}, minimum = "1", description = "Redemptions allowed per user; defaults to 1", example = "1")
    private Integer perUserLimit;

    @Schema(types = {"boolean", "null"}, description = "Whether the coupon can be used; defaults to true", example = "true")
    private Boolean active;
}
