package com.awesome.testing.service.tax;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.tax.TaxPreviewDto;
import com.awesome.testing.entity.tax.VatRateEntity;
import com.awesome.testing.service.CartService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartTaxPreviewServiceTest {

    @Mock private CartService cartService;
    @Mock private VatRateService vatRateService;
    @InjectMocks private CartTaxPreviewService service;

    @Test
    void previewsTheCartTotalWithoutCoupon() {
        when(vatRateService.rateFor("Poland")).thenReturn(Optional.of(rate("PL", "23")));
        when(cartService.getCart("buyer")).thenReturn(CartDto.builder().totalPrice(new BigDecimal("123.00")).build());

        TaxPreviewDto preview = service.preview("buyer", "Poland");

        assertThat(preview.getCountry()).isEqualTo("PL");
        assertThat(preview.getRate()).hasToString("23.00");
        assertThat(preview.getTaxAmount()).hasToString("23.00");
        assertThat(preview.getNetAmount()).hasToString("100.00");
        assertThat(preview.getTotalAmount()).hasToString("123.00");
    }

    @Test
    void previewsTheTotalAfterTheCouponDiscount() {
        when(vatRateService.rateFor("HU")).thenReturn(Optional.of(rate("HU", "27")));
        when(cartService.getCart("buyer")).thenReturn(CartDto.builder().totalPrice(new BigDecimal("200.00"))
                .totalAfterDiscount(new BigDecimal("127.00")).build());

        TaxPreviewDto preview = service.preview("buyer", "HU");

        assertThat(preview.getTotalAmount()).isEqualByComparingTo("127.00");
        assertThat(preview.getTaxAmount()).isEqualByComparingTo("27.00");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void requiresACountry(String country) {
        assertThatThrownBy(() -> service.preview("buyer", country))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("country is required");
                });
        verifyNoInteractions(vatRateService, cartService);
    }

    @Test
    void unknownCountriesAreNotFound() {
        when(vatRateService.rateFor("Atlantis")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview("buyer", "Atlantis"))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(cartService);
    }

    private static VatRateEntity rate(String code, String rate) {
        return VatRateEntity.builder().countryCode(code).countryName(code).rate(new BigDecimal(rate)).build();
    }
}
