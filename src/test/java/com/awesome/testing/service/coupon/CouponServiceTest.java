package com.awesome.testing.service.coupon;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.coupon.CouponRequestDto;
import com.awesome.testing.dto.coupon.CouponStatus;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.coupon.CartCouponEntity;
import com.awesome.testing.entity.coupon.CouponEntity;
import com.awesome.testing.entity.coupon.CouponRedemptionEntity;
import com.awesome.testing.entity.coupon.CouponType;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.coupon.CartCouponRepository;
import com.awesome.testing.repository.coupon.CouponRedemptionRepository;
import com.awesome.testing.repository.coupon.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 12, 0);
    private static final String USER = "buyer";

    @Mock private CouponRepository couponRepository;
    @Mock private CouponRedemptionRepository redemptionRepository;
    @Mock private CartCouponRepository cartCouponRepository;
    @Mock private CartItemRepository cartItemRepository;

    private CouponService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new CouponService(couponRepository, redemptionRepository, cartCouponRepository, cartItemRepository, clock);
    }

    private static CouponEntity coupon() {
        return CouponEntity.builder().id(1L).code("SAVE").type(CouponType.PERCENTAGE).value(BigDecimal.TEN)
                .perUserLimit(1).redemptionCount(0).active(true).build();
    }

    private Optional<String> failure(CouponEntity coupon, String subtotal) {
        return service.evaluate(coupon, USER, new BigDecimal(subtotal)).map(CouponService.Failure::message);
    }

    @Test
    void validityWindowBoundariesAreInclusive() {
        CouponEntity coupon = coupon();
        coupon.setValidFrom(NOW);
        coupon.setValidUntil(NOW);

        assertThat(failure(coupon, "10.00")).isEmpty();

        coupon.setValidFrom(NOW.plusSeconds(1));
        assertThat(failure(coupon, "10.00")).contains("Coupon is not valid yet");
        coupon.setValidFrom(null);
        coupon.setValidUntil(NOW.minusSeconds(1));
        assertThat(failure(coupon, "10.00")).contains("Coupon has expired");
    }

    @Test
    void minimumSubtotalIsInclusiveAndFormattedWithCents() {
        CouponEntity coupon = coupon();
        coupon.setMinOrderAmount(new BigDecimal("50"));

        assertThat(failure(coupon, "50.00")).isEmpty();
        assertThat(failure(coupon, "49.99")).contains("Order subtotal must be at least 50.00");
    }

    @Test
    void globalLimitIsReachedWhenTheCountEqualsTheMaximum() {
        CouponEntity coupon = coupon();
        coupon.setMaxRedemptions(2);
        coupon.setRedemptionCount(1);
        assertThat(failure(coupon, "10.00")).isEmpty();

        coupon.setRedemptionCount(2);
        Optional<CouponService.Failure> exhausted = service.evaluate(coupon, USER, BigDecimal.TEN);
        assertThat(exhausted).get().extracting(CouponService.Failure::status).isEqualTo(HttpStatus.CONFLICT);
        assertThat(service.statusOf(coupon)).isEqualTo(CouponStatus.EXHAUSTED);
    }

    @Test
    void perUserLimitCountsOnlyThisUsersRedemptions() {
        CouponEntity coupon = coupon();
        coupon.setPerUserLimit(2);
        when(redemptionRepository.countByCouponIdAndUsername(1L, USER)).thenReturn(1L, 2L);

        assertThat(failure(coupon, "10.00")).isEmpty();
        assertThat(failure(coupon, "10.00")).contains("Coupon already used the maximum number of times");
    }

    @Test
    void inactivityIsReportedBeforeAnyOtherProblem() {
        CouponEntity coupon = coupon();
        coupon.setActive(false);
        coupon.setValidUntil(NOW.minusDays(1));
        coupon.setMinOrderAmount(new BigDecimal("100"));

        assertThat(failure(coupon, "1.00")).contains("Coupon is not active");
        assertThat(service.statusOf(coupon)).isEqualTo(CouponStatus.INACTIVE);
    }

    @Test
    void checkoutQuoteRejectsACouponThatStoppedQualifyingWithAConflict() {
        CouponEntity coupon = coupon();
        coupon.setMinOrderAmount(new BigDecimal("100"));
        when(cartCouponRepository.findById(USER)).thenReturn(Optional.of(CartCouponEntity.builder().username(USER).coupon(coupon).build()));
        when(couponRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.quoteForCheckout(USER, new BigDecimal("99.99")))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Applied coupon is no longer valid: Order subtotal must be at least 100.00");
                });
    }

    @Test
    void checkoutQuoteLocksTheCouponAndPricesTheDiscount() {
        CouponEntity coupon = coupon();
        when(cartCouponRepository.findById(USER)).thenReturn(Optional.of(CartCouponEntity.builder().username(USER).coupon(coupon).build()));
        when(couponRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(coupon));

        assertThat(service.quoteForCheckout(USER, new BigDecimal("99.99")))
                .get().extracting(CouponQuote::discount).isEqualTo(new BigDecimal("10.00"));
        verify(couponRepository, never()).findById(any());
    }

    @Test
    void redemptionIncrementsTheCountAndClearsTheCartCoupon() {
        CouponEntity coupon = coupon();
        OrderEntity order = OrderEntity.builder().id(9L).username(USER).build();

        service.recordRedemption(new CouponQuote(coupon, new BigDecimal("5.00")), order);

        assertThat(coupon.getRedemptionCount()).isEqualTo(1);
        ArgumentCaptor<CouponRedemptionEntity> saved = ArgumentCaptor.forClass(CouponRedemptionEntity.class);
        verify(redemptionRepository).save(saved.capture());
        assertThat(saved.getValue().getDiscountAmount()).isEqualByComparingTo("5.00");
        assertThat(saved.getValue().getOrder()).isSameAs(order);
        verify(cartCouponRepository).deleteByUsername(USER);
    }

    @Test
    void releasingARedemptionReturnsOneUseAndNeverGoesNegative() {
        CouponEntity coupon = coupon();
        coupon.setRedemptionCount(2);
        OrderEntity order = OrderEntity.builder().id(9L).username(USER).build();
        CouponRedemptionEntity redemption = CouponRedemptionEntity.builder().coupon(coupon).order(order).build();
        when(redemptionRepository.findByOrderId(9L)).thenReturn(Optional.of(redemption));
        when(couponRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(coupon));

        service.releaseRedemption(order);
        assertThat(coupon.getRedemptionCount()).isEqualTo(1);
        verify(redemptionRepository).delete(redemption);

        coupon.setRedemptionCount(0);
        service.releaseRedemption(order);
        assertThat(coupon.getRedemptionCount()).isZero();
    }

    @Test
    void applyingToAnEmptyCartIsRejectedBeforeLookingUpTheCode() {
        when(cartItemRepository.findByUsername(USER)).thenReturn(List.of());

        assertThatThrownBy(() -> service.applyToCart(USER, "save"))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getMessage()).isEqualTo("Cart is empty"));
        verify(couponRepository, never()).findByCode(any());
    }

    @Test
    void definitionsAreNormalisedAndValidated() {
        when(couponRepository.existsByCode("WELCOME-10")).thenReturn(false);
        when(couponRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(CouponRequestDto.builder().code(" welcome-10 ").type(CouponType.FIXED_AMOUNT)
                .value(new BigDecimal("150")).build());

        assertThat(created.getCode()).isEqualTo("WELCOME-10");
        assertThat(created.getType()).isEqualTo(CouponType.FIXED_AMOUNT);
        assertThat(created.getValue()).isEqualByComparingTo("150");
        assertThat(created.getPerUserLimit()).isEqualTo(1);
        assertThat(created.getActive()).isTrue();
        assertThat(created.getStatus()).isEqualTo(CouponStatus.ACTIVE);

        when(couponRepository.existsByCode("FULL")).thenReturn(false);
        var full = service.create(CouponRequestDto.builder().code("full").description("All of it").type(CouponType.PERCENTAGE)
                .value(new BigDecimal("100")).minOrderAmount(BigDecimal.ONE).validFrom(NOW.minusDays(1)).validUntil(NOW.plusDays(1))
                .maxRedemptions(5).perUserLimit(2).active(false).build());
        assertThat(full).satisfies(dto -> {
            assertThat(dto.getDescription()).isEqualTo("All of it");
            assertThat(dto.getValue()).isEqualByComparingTo("100");
            assertThat(dto.getMinOrderAmount()).isEqualByComparingTo("1");
            assertThat(dto.getValidFrom()).isEqualTo(NOW.minusDays(1));
            assertThat(dto.getValidUntil()).isEqualTo(NOW.plusDays(1));
            assertThat(dto.getMaxRedemptions()).isEqualTo(5);
            assertThat(dto.getPerUserLimit()).isEqualTo(2);
            assertThat(dto.getActive()).isFalse();
        });
        assertThatThrownBy(() -> service.create(CouponRequestDto.builder().code("X1X").type(CouponType.PERCENTAGE)
                .value(new BigDecimal("100.01")).build())).hasMessage("Percentage coupons cannot exceed 100");
        assertThatThrownBy(() -> service.create(CouponRequestDto.builder().code("X2X").type(CouponType.PERCENTAGE)
                .value(BigDecimal.ONE).validFrom(NOW).validUntil(NOW).build())).hasMessage("validUntil must be after validFrom");
    }

    @Test
    void clockIsHonouredForScheduledStatus() {
        CouponEntity coupon = coupon();
        coupon.setValidFrom(LocalDateTime.ofInstant(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC));

        assertThat(service.statusOf(coupon)).isEqualTo(CouponStatus.SCHEDULED);
    }
}
