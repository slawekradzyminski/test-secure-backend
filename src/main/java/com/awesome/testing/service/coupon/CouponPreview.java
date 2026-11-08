package com.awesome.testing.service.coupon;

import java.math.BigDecimal;

/** Cart view of an applied coupon. */
public record CouponPreview(String code, boolean valid, String message, BigDecimal discount) {
}
