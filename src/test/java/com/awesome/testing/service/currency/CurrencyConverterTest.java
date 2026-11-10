package com.awesome.testing.service.currency;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyConverterTest {

    @ParameterizedTest(name = "{0} at {1}->{2} = {3} (rate {4})")
    @CsvSource({
            "100.00, 1, 1.08, 92.59, 0.925926",
            "100.00, 1.08, 1, 108.00, 1.080000",
            "100.00, 0.25, 1.08, 23.15, 0.231481",
            "100.00, 1.08, 0.25, 432.00, 4.320000",
            "1.00, 1, 3, 0.33, 0.333333",
            "2.00, 1, 3, 0.67, 0.333333",
            "0.01, 1, 2, 0.01, 0.500000",
            "0.01, 1, 2.000001, 0.00, 0.500000",
            "0.00, 1, 1.08, 0.00, 0.925926",
            "123.45, 1.27, 1.27, 123.45, 1.000000",
            "99999999.99, 1.13, 0.25, 451999999.95, 4.520000"})
    void convertsThroughUsdRates(String amount, String from, String to, String converted, String rate) {
        BigDecimal value = new BigDecimal(amount);

        assertThat(CurrencyConverter.convert(value, new BigDecimal(from), new BigDecimal(to))).hasToString(converted);
        assertThat(CurrencyConverter.rate(new BigDecimal(from), new BigDecimal(to))).hasToString(rate);
    }
}
