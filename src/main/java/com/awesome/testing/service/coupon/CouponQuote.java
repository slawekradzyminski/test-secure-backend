package com.awesome.testing.service.coupon;

import com.awesome.testing.entity.coupon.CouponEntity;

import java.math.BigDecimal;

/** A coupon that passed checkout validation, locked for the current transaction. */
public record CouponQuote(CouponEntity coupon, BigDecimal discount) {
}
