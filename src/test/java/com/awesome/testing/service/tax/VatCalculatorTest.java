package com.awesome.testing.service.tax;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class VatCalculatorTest {

    @ParameterizedTest(name = "{0} at {1}% -> tax {2}, net {3}")
    @CsvSource({
            "123.00, 23, 23.00, 100.00",
            "100.00, 23, 18.70, 81.30",
            "99.99, 23, 18.70, 81.29",
            "127.00, 27, 27.00, 100.00",
            "10.00, 27, 2.13, 7.87",
            "1.00, 19, 0.16, 0.84",
            "0.01, 23, 0.00, 0.01",
            "0.05, 23, 0.01, 0.04",
            "108.10, 8.1, 8.10, 100.00",
            "100.00, 25.5, 20.32, 79.68",
            "100.00, 0, 0.00, 100.00",
            "0.00, 23, 0.00, 0.00",
            "50.00, 50, 16.67, 33.33"})
    void extractsTheVatContainedInAGrossAmount(String gross, String rate, String tax, String net) {
        VatBreakdown breakdown = VatCalculator.breakdown(new BigDecimal(gross), new BigDecimal(rate));

        assertThat(breakdown.taxAmount()).isEqualByComparingTo(tax);
        assertThat(breakdown.netAmount()).isEqualByComparingTo(net);
        assertThat(breakdown.totalAmount()).isEqualByComparingTo(gross);
        assertThat(breakdown.netAmount().add(breakdown.taxAmount())).isEqualByComparingTo(gross);
    }

    @Test
    void roundsTheVatHalfUpAtTheExactCentBoundary() {
        // 0.43 * 21 / 121 = 0.074628... -> 0.07; 2.42 * 21 / 121 = 0.42 exactly; 0.69 * 23 / 123 = 0.12902 -> 0.13
        assertThat(VatCalculator.taxIn(new BigDecimal("0.43"), new BigDecimal("21"))).isEqualByComparingTo("0.07");
        assertThat(VatCalculator.taxIn(new BigDecimal("2.42"), new BigDecimal("21"))).isEqualByComparingTo("0.42");
        assertThat(VatCalculator.taxIn(new BigDecimal("0.69"), new BigDecimal("23"))).isEqualByComparingTo("0.13");
        // 0.75 * 20 / 120 = 0.125 exactly: half-up gives 0.13 where half-even would give 0.12
        assertThat(VatCalculator.taxIn(new BigDecimal("0.75"), new BigDecimal("20"))).isEqualByComparingTo("0.13");
    }

    @Test
    void returnsScaleTwoAmounts() {
        VatBreakdown breakdown = VatCalculator.breakdown(new BigDecimal("100"), new BigDecimal("23"));

        assertThat(breakdown.rate()).hasToString("23.00");
        assertThat(breakdown.taxAmount()).hasToString("18.70");
        assertThat(breakdown.netAmount()).hasToString("81.30");
        assertThat(breakdown.totalAmount()).hasToString("100.00");
        assertThat(VatCalculator.taxIn(BigDecimal.TEN, BigDecimal.ZERO)).hasToString("0.00");
        assertThat(VatCalculator.taxIn(BigDecimal.ZERO, BigDecimal.TEN)).hasToString("0.00");
    }

    @Test
    void negativeAmountsCarryNoVat() {
        assertThat(VatCalculator.taxIn(new BigDecimal("-10.00"), new BigDecimal("23"))).isEqualByComparingTo("0");
    }
}
