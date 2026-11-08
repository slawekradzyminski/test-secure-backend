package com.awesome.testing.dto.coupon;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Coupon code to apply to the cart")
public class ApplyCouponDto {

    @NotBlank(message = "Coupon code is required")
    @Size(max = 64, message = "Coupon code is too long")
    @Schema(minLength = 1, pattern = "\\S", maxLength = 64, description = "Case-insensitive coupon code", example = "welcome10")
    private String code;
}
