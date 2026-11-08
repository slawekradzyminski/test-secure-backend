package com.awesome.testing.repository.coupon;

import com.awesome.testing.entity.coupon.CouponRedemptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemptionEntity, Long> {

    long countByCouponIdAndUsername(Long couponId, String username);

    boolean existsByCouponId(Long couponId);

    Optional<CouponRedemptionEntity> findByOrderId(Long orderId);
}
