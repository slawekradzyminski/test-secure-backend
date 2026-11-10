package com.awesome.testing.service.tax;

import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.service.checkout.CheckoutContext;
import com.awesome.testing.service.checkout.CheckoutContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Records the VAT contained in the order total (prices are VAT-inclusive, so
 * the total itself is unchanged). The rate is copied onto the order, so later
 * rate changes leave existing orders alone. Unknown countries get no tax
 * fields and checkout continues.
 */
@Component
@Order(VatCheckoutContributor.ORDER)
@RequiredArgsConstructor
public class VatCheckoutContributor implements CheckoutContributor {

    /** Runs after contributors that might adjust the total. */
    public static final int ORDER = 100;

    private final VatRateService vatRateService;

    @Override
    public void contribute(CheckoutContext context) {
        OrderEntity order = context.order();
        String country = context.address() == null ? null : context.address().getCountry();
        vatRateService.rateFor(country).ifPresentOrElse(rate -> {
            VatBreakdown breakdown = VatCalculator.breakdown(order.getTotalAmount(), rate.getRate());
            order.setTaxCountry(rate.getCountryCode());
            order.setTaxRate(breakdown.rate());
            order.setTaxAmount(breakdown.taxAmount());
            order.setNetAmount(breakdown.netAmount());
        }, () -> {
            order.setTaxCountry(null);
            order.setTaxRate(null);
            order.setTaxAmount(null);
            order.setNetAmount(null);
        });
    }
}
