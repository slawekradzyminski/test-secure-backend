package com.awesome.testing.endpoints;

import com.awesome.testing.repository.OrderIdempotencyKeyRepository;
import com.awesome.testing.repository.coupon.CartCouponRepository;
import com.awesome.testing.repository.coupon.CouponRedemptionRepository;
import com.awesome.testing.repository.coupon.CouponRepository;
import com.awesome.testing.repository.review.ProductReviewRepository;
import com.awesome.testing.repository.wishlist.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Removes rows of the commerce tables that reference orders and products. */
@Component
@RequiredArgsConstructor
public class CommerceTestData {

    private final CouponRedemptionRepository couponRedemptionRepository;
    private final CartCouponRepository cartCouponRepository;
    private final CouponRepository couponRepository;
    private final ProductReviewRepository productReviewRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final OrderIdempotencyKeyRepository orderIdempotencyKeyRepository;

    @Transactional
    public void deleteAll() {
        couponRedemptionRepository.deleteAllInBatch();
        cartCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        productReviewRepository.deleteAllInBatch();
        wishlistItemRepository.deleteAllInBatch();
        orderIdempotencyKeyRepository.deleteAllInBatch();
    }
}
