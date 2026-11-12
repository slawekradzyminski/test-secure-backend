package com.awesome.testing.graphql;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CommerceViewsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "12.5, 12.50",
            "12.50, 12.50",
            "12, 12.00",
            "0.1, 0.10",
            "1234567.8, 1234567.80",
            "1.234, 1.234",
            "0, 0",
            "0.00, 0",
            "-3.5, -3.50"
    })
    void moneyHasTwoDecimalsExceptZero(String amount, String expected) {
        assertThat(CommerceViews.money(new BigDecimal(amount))).isEqualTo(expected);
    }
}
