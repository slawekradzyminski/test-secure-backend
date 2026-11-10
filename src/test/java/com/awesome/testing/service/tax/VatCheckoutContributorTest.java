package com.awesome.testing.service.tax;

import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.tax.VatRateEntity;
import com.awesome.testing.service.checkout.CheckoutContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.Order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VatCheckoutContributorTest {

    @Mock private VatRateService vatRateService;

    @Test
    void storesTheVatContainedInTheDiscountedTotal() {
        when(vatRateService.rateFor("Polska")).thenReturn(Optional.of(rate("PL", "23")));
        OrderEntity order = order("200.00", "40.00", "160.00");

        contributor().contribute(context("Polska", order));

        assertThat(order.getTaxCountry()).isEqualTo("PL");
        assertThat(order.getTaxRate()).hasToString("23.00");
        assertThat(order.getTaxAmount()).hasToString("29.92");
        assertThat(order.getNetAmount()).hasToString("130.08");
        assertThat(order.getTotalAmount()).hasToString("160.00");
        assertThat(order.getSubtotalAmount()).hasToString("200.00");
    }

    @Test
    void recordsAZeroRateForCountriesWithoutVat() {
        when(vatRateService.rateFor("USA")).thenReturn(Optional.of(rate("US", "0")));
        OrderEntity order = order("10.00", "0", "10.00");

        contributor().contribute(context("USA", order));

        assertThat(order.getTaxCountry()).isEqualTo("US");
        assertThat(order.getTaxRate()).isEqualByComparingTo("0");
        assertThat(order.getTaxAmount()).isEqualByComparingTo("0");
        assertThat(order.getNetAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void leavesEveryTaxFieldEmptyForUnknownCountries() {
        when(vatRateService.rateFor("Atlantis")).thenReturn(Optional.empty());
        OrderEntity order = order("10.00", "0", "10.00");
        order.setTaxCountry("PL");
        order.setTaxRate(BigDecimal.ONE);
        order.setTaxAmount(BigDecimal.ONE);
        order.setNetAmount(BigDecimal.ONE);

        contributor().contribute(context("Atlantis", order));

        assertThat(order.getTaxCountry()).isNull();
        assertThat(order.getTaxRate()).isNull();
        assertThat(order.getTaxAmount()).isNull();
        assertThat(order.getNetAmount()).isNull();
        assertThat(order.getTotalAmount()).hasToString("10.00");
    }

    @Test
    void toleratesAMissingAddress() {
        when(vatRateService.rateFor(null)).thenReturn(Optional.empty());
        OrderEntity order = order("10.00", "0", "10.00");

        contributor().contribute(new CheckoutContext("buyer", null, List.of(), order));

        assertThat(order.getTaxCountry()).isNull();
    }

    @Test
    void runsAfterDefaultOrderedContributors() {
        assertThat(VatCheckoutContributor.class.getAnnotation(Order.class).value()).isEqualTo(100);
    }

    private VatCheckoutContributor contributor() {
        return new VatCheckoutContributor(vatRateService);
    }

    private static CheckoutContext context(String country, OrderEntity order) {
        return new CheckoutContext("buyer", AddressDto.builder().country(country).build(), List.of(), order);
    }

    private static OrderEntity order(String subtotal, String discount, String total) {
        return OrderEntity.builder().username("buyer").subtotalAmount(new BigDecimal(subtotal))
                .discountAmount(new BigDecimal(discount)).totalAmount(new BigDecimal(total)).build();
    }

    private static VatRateEntity rate(String code, String rate) {
        return VatRateEntity.builder().countryCode(code).countryName(code).rate(new BigDecimal(rate)).build();
    }
}
