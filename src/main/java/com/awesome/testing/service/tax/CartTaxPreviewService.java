package com.awesome.testing.service.tax;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.tax.TaxPreviewDto;
import com.awesome.testing.entity.tax.VatRateEntity;
import com.awesome.testing.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** VAT preview for the current cart; the cart response itself is never changed. */
@Service
@RequiredArgsConstructor
public class CartTaxPreviewService {

    private final CartService cartService;
    private final VatRateService vatRateService;

    public TaxPreviewDto preview(String username, String country) {
        if (country == null || country.isBlank()) {
            throw new CustomException("country is required", HttpStatus.BAD_REQUEST);
        }
        VatRateEntity rate = vatRateService.rateFor(country).orElseThrow(() -> VatRateService.notFound(country));
        CartDto cart = cartService.getCart(username);
        BigDecimal total = cart.getTotalAfterDiscount() != null ? cart.getTotalAfterDiscount() : cart.getTotalPrice();
        return TaxPreviewDto.from(rate.getCountryCode(), VatCalculator.breakdown(total, rate.getRate()));
    }
}
