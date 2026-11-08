package com.awesome.testing.endpoints;

import com.awesome.testing.repository.OrderIdempotencyKeyRepository;
import com.awesome.testing.repository.coupon.CartCouponRepository;
import com.awesome.testing.repository.coupon.CouponRedemptionRepository;
import com.awesome.testing.repository.coupon.CouponRepository;
import com.awesome.testing.repository.fulfilment.ShipmentRepository;
import com.awesome.testing.repository.purchasing.PurchaseOrderRepository;
import com.awesome.testing.repository.purchasing.PurchaseReceiptRepository;
import com.awesome.testing.repository.purchasing.SupplierRepository;
import com.awesome.testing.repository.returns.ReturnRequestRepository;
import com.awesome.testing.repository.review.ProductReviewRepository;
import com.awesome.testing.repository.wishlist.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Removes rows of the commerce and fulfilment tables that reference orders and products. */
@Component
@RequiredArgsConstructor
public class CommerceTestData {

    private final PurchaseReceiptRepository purchaseReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ShipmentRepository shipmentRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final CartCouponRepository cartCouponRepository;
    private final CouponRepository couponRepository;
    private final ProductReviewRepository productReviewRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final OrderIdempotencyKeyRepository orderIdempotencyKeyRepository;

    @Transactional
    public void deleteAll() {
        purchaseReceiptRepository.deleteAllInBatch();
        purchaseOrderRepository.deleteAll();
        purchaseOrderRepository.flush();
        supplierRepository.deleteAllInBatch();
        returnRequestRepository.deleteAll();
        returnRequestRepository.flush();
        shipmentRepository.deleteAllInBatch();
        couponRedemptionRepository.deleteAllInBatch();
        cartCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        productReviewRepository.deleteAllInBatch();
        wishlistItemRepository.deleteAllInBatch();
        orderIdempotencyKeyRepository.deleteAllInBatch();
    }
}
