package com.awesome.testing.service.coupon;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.coupon.CouponDto;
import com.awesome.testing.dto.coupon.CouponRequestDto;
import com.awesome.testing.dto.coupon.CouponStatus;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.coupon.CartCouponEntity;
import com.awesome.testing.entity.coupon.CouponEntity;
import com.awesome.testing.entity.coupon.CouponRedemptionEntity;
import com.awesome.testing.entity.coupon.CouponType;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.coupon.CartCouponRepository;
import com.awesome.testing.repository.coupon.CouponRedemptionRepository;
import com.awesome.testing.repository.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CouponService {

    private static final BigDecimal MAX_PERCENTAGE = BigDecimal.valueOf(100);

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final CartCouponRepository cartCouponRepository;
    private final CartItemRepository cartItemRepository;
    private final Clock commerceClock;

    @Transactional(readOnly = true)
    public Page<CouponDto> list(Pageable pageable) {
        return couponRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public CouponDto get(Long id) {
        return toDto(findCoupon(id));
    }

    @Transactional
    public CouponDto create(CouponRequestDto request) {
        String code = normalize(request.getCode());
        validateDefinition(request);
        if (couponRepository.existsByCode(code)) {
            throw new CustomException("Coupon code already exists", HttpStatus.CONFLICT);
        }
        CouponEntity coupon = CouponEntity.builder().code(code).redemptionCount(0).build();
        apply(request, coupon);
        return toDto(couponRepository.saveAndFlush(coupon));
    }

    @Transactional
    public CouponDto update(Long id, CouponRequestDto request) {
        CouponEntity coupon = findCoupon(id);
        String code = normalize(request.getCode());
        validateDefinition(request);
        if (!code.equals(coupon.getCode()) && couponRepository.existsByCode(code)) {
            throw new CustomException("Coupon code already exists", HttpStatus.CONFLICT);
        }
        coupon.setCode(code);
        apply(request, coupon);
        return toDto(couponRepository.saveAndFlush(coupon));
    }

    @Transactional
    public void delete(Long id) {
        CouponEntity coupon = findCoupon(id);
        if (redemptionRepository.existsByCouponId(id)) {
            throw new CustomException("Coupon has redemptions; deactivate it instead", HttpStatus.CONFLICT);
        }
        couponRepository.delete(coupon);
    }

    /** Applies a coupon to the user's cart after validating it against the current subtotal. */
    @Transactional
    public void applyToCart(String username, String rawCode) {
        BigDecimal subtotal = subtotal(cartItemRepository.findByUsername(username));
        if (subtotal.signum() == 0) {
            throw new CustomException("Cart is empty", HttpStatus.BAD_REQUEST);
        }
        CouponEntity coupon = couponRepository.findByCode(normalize(rawCode))
                .orElseThrow(() -> new CustomException("Coupon not found", HttpStatus.NOT_FOUND));
        evaluate(coupon, username, subtotal).ifPresent(failure -> {
            throw new CustomException(failure.message(), failure.status());
        });
        CartCouponEntity cartCoupon = cartCouponRepository.findById(username)
                .orElseGet(() -> CartCouponEntity.builder().username(username).build());
        cartCoupon.setCoupon(coupon);
        cartCoupon.setAppliedAt(LocalDateTime.now(commerceClock));
        cartCouponRepository.save(cartCoupon);
    }

    @Transactional
    public void removeFromCart(String username) {
        cartCouponRepository.deleteByUsername(username);
    }

    @Transactional(readOnly = true)
    public Optional<CouponPreview> preview(String username, BigDecimal subtotal) {
        return cartCouponRepository.findById(username).map(cartCoupon -> {
            CouponEntity coupon = cartCoupon.getCoupon();
            Optional<Failure> failure = evaluate(coupon, username, subtotal);
            return failure
                    .map(f -> new CouponPreview(coupon.getCode(), false, f.message(), BigDecimal.ZERO))
                    .orElseGet(() -> new CouponPreview(coupon.getCode(), true, null,
                            CouponPricing.discount(coupon.getType(), coupon.getValue(), subtotal)));
        });
    }

    /**
     * Re-validates the cart coupon under a row lock so concurrent checkouts
     * cannot exceed redemption limits. Returns empty when no coupon is applied.
     */
    @Transactional
    public Optional<CouponQuote> quoteForCheckout(String username, BigDecimal subtotal) {
        Optional<CartCouponEntity> cartCoupon = cartCouponRepository.findById(username);
        if (cartCoupon.isEmpty()) {
            return Optional.empty();
        }
        CouponEntity coupon = couponRepository.findByIdForUpdate(cartCoupon.get().getCoupon().getId())
                .orElseThrow(() -> new CustomException("Coupon not found", HttpStatus.NOT_FOUND));
        evaluate(coupon, username, subtotal).ifPresent(failure -> {
            throw new CustomException(
                    "Applied coupon is no longer valid: " + failure.message(), HttpStatus.CONFLICT);
        });
        return Optional.of(new CouponQuote(
                coupon, CouponPricing.discount(coupon.getType(), coupon.getValue(), subtotal)));
    }

    @Transactional
    public void recordRedemption(CouponQuote quote, OrderEntity order) {
        CouponEntity coupon = quote.coupon();
        coupon.setRedemptionCount(coupon.getRedemptionCount() + 1);
        couponRepository.save(coupon);
        redemptionRepository.save(CouponRedemptionEntity.builder()
                .coupon(coupon)
                .order(order)
                .username(order.getUsername())
                .discountAmount(quote.discount())
                .build());
        cartCouponRepository.deleteByUsername(order.getUsername());
    }

    /** Returns the coupon usage consumed by a cancelled order. */
    @Transactional
    public void releaseRedemption(OrderEntity order) {
        redemptionRepository.findByOrderId(order.getId()).ifPresent(redemption -> {
            CouponEntity coupon = couponRepository.findByIdForUpdate(redemption.getCoupon().getId())
                    .orElseThrow(() -> new CustomException("Coupon not found", HttpStatus.NOT_FOUND));
            coupon.setRedemptionCount(Math.max(0, coupon.getRedemptionCount() - 1));
            couponRepository.save(coupon);
            redemptionRepository.delete(redemption);
        });
    }

    Optional<Failure> evaluate(CouponEntity coupon, String username, BigDecimal subtotal) {
        LocalDateTime now = LocalDateTime.now(commerceClock);
        if (!Boolean.TRUE.equals(coupon.getActive())) {
            return failure(HttpStatus.BAD_REQUEST, "Coupon is not active");
        }
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            return failure(HttpStatus.BAD_REQUEST, "Coupon is not valid yet");
        }
        if (coupon.getValidUntil() != null && now.isAfter(coupon.getValidUntil())) {
            return failure(HttpStatus.BAD_REQUEST, "Coupon has expired");
        }
        if (coupon.getMaxRedemptions() != null && coupon.getRedemptionCount() >= coupon.getMaxRedemptions()) {
            return failure(HttpStatus.CONFLICT, "Coupon usage limit reached");
        }
        if (redemptionRepository.countByCouponIdAndUsername(coupon.getId(), username) >= coupon.getPerUserLimit()) {
            return failure(HttpStatus.CONFLICT, "Coupon already used the maximum number of times");
        }
        if (coupon.getMinOrderAmount() != null && subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            return failure(HttpStatus.BAD_REQUEST,
                    "Order subtotal must be at least " + coupon.getMinOrderAmount().setScale(2));
        }
        return Optional.empty();
    }

    CouponStatus statusOf(CouponEntity coupon) {
        LocalDateTime now = LocalDateTime.now(commerceClock);
        if (!Boolean.TRUE.equals(coupon.getActive())) {
            return CouponStatus.INACTIVE;
        }
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            return CouponStatus.SCHEDULED;
        }
        if (coupon.getValidUntil() != null && now.isAfter(coupon.getValidUntil())) {
            return CouponStatus.EXPIRED;
        }
        if (coupon.getMaxRedemptions() != null && coupon.getRedemptionCount() >= coupon.getMaxRedemptions()) {
            return CouponStatus.EXHAUSTED;
        }
        return CouponStatus.ACTIVE;
    }

    public static BigDecimal subtotal(List<CartItemEntity> items) {
        return items.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateDefinition(CouponRequestDto request) {
        if (request.getType() == CouponType.PERCENTAGE && request.getValue().compareTo(MAX_PERCENTAGE) > 0) {
            throw new CustomException("Percentage coupons cannot exceed 100", HttpStatus.BAD_REQUEST);
        }
        if (request.getValidFrom() != null && request.getValidUntil() != null
                && !request.getValidUntil().isAfter(request.getValidFrom())) {
            throw new CustomException("validUntil must be after validFrom", HttpStatus.BAD_REQUEST);
        }
    }

    private void apply(CouponRequestDto request, CouponEntity coupon) {
        coupon.setDescription(request.getDescription());
        coupon.setType(request.getType());
        coupon.setValue(request.getValue());
        coupon.setMinOrderAmount(request.getMinOrderAmount());
        coupon.setValidFrom(request.getValidFrom());
        coupon.setValidUntil(request.getValidUntil());
        coupon.setMaxRedemptions(request.getMaxRedemptions());
        coupon.setPerUserLimit(request.getPerUserLimit() == null ? 1 : request.getPerUserLimit());
        coupon.setActive(request.getActive() == null || request.getActive());
    }

    private CouponEntity findCoupon(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new CustomException("Coupon not found", HttpStatus.NOT_FOUND));
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static Optional<Failure> failure(HttpStatus status, String message) {
        return Optional.of(new Failure(status, message));
    }

    private CouponDto toDto(CouponEntity coupon) {
        return CouponDto.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .description(coupon.getDescription())
                .type(coupon.getType())
                .value(coupon.getValue())
                .minOrderAmount(coupon.getMinOrderAmount())
                .validFrom(coupon.getValidFrom())
                .validUntil(coupon.getValidUntil())
                .maxRedemptions(coupon.getMaxRedemptions())
                .perUserLimit(coupon.getPerUserLimit())
                .redemptionCount(coupon.getRedemptionCount())
                .active(coupon.getActive())
                .status(statusOf(coupon))
                .createdAt(coupon.getCreatedAt())
                .updatedAt(coupon.getUpdatedAt())
                .build();
    }

    record Failure(HttpStatus status, String message) {
    }
}
