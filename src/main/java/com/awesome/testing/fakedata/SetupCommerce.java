package com.awesome.testing.fakedata;

import com.awesome.testing.entity.coupon.CouponEntity;
import com.awesome.testing.entity.coupon.CouponType;
import com.awesome.testing.repository.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Demo coupons covering the main validation branches. Suppliers live in warehouse-service. */
@Component
@RequiredArgsConstructor
public class SetupCommerce {

    private final CouponRepository couponRepository;

    @Transactional
    public void createCoupons() {
        LocalDateTime now = LocalDateTime.now();
        coupon("WELCOME10", "10% off orders from 50.00", CouponType.PERCENTAGE, "10", "50.00", null, null, null, 1);
        coupon("SAVE50", "50.00 off orders from 300.00", CouponType.FIXED_AMOUNT, "50", "300.00", null, null, 100, 1);
        coupon("EXPIRED20", "Expired campaign", CouponType.PERCENTAGE, "20", null,
                now.minusDays(30), now.minusDays(1), null, 1);
        coupon("SPRING25", "Starts in a week", CouponType.PERCENTAGE, "25", null, now.plusDays(7), now.plusDays(37), null, 1);
    }

    private void coupon(String code, String description, CouponType type, String value, String minOrder,
                        LocalDateTime validFrom, LocalDateTime validUntil, Integer maxRedemptions, int perUserLimit) {
        if (couponRepository.existsByCode(code)) {
            return;
        }
        couponRepository.save(CouponEntity.builder()
                .code(code)
                .description(description)
                .type(type)
                .value(new BigDecimal(value))
                .minOrderAmount(minOrder == null ? null : new BigDecimal(minOrder))
                .validFrom(validFrom)
                .validUntil(validUntil)
                .maxRedemptions(maxRedemptions)
                .perUserLimit(perUserLimit)
                .redemptionCount(0)
                .active(true)
                .build());
    }
}
