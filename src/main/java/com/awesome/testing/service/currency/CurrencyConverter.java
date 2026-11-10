package com.awesome.testing.service.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conversion through USD rates ({@code rateToUsd} = USD value of one unit).
 * The converted amount uses the exact ratio and is rounded half-up to cents
 * once; the reported rate is rounded half-up to 6 decimals for display.
 */
public final class CurrencyConverter {

    public static final int RATE_SCALE = 6;

    private CurrencyConverter() {
    }

    public static BigDecimal convert(BigDecimal amount, BigDecimal fromRateToUsd, BigDecimal toRateToUsd) {
        return amount.multiply(fromRateToUsd).divide(toRateToUsd, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal rate(BigDecimal fromRateToUsd, BigDecimal toRateToUsd) {
        return fromRateToUsd.divide(toRateToUsd, RATE_SCALE, RoundingMode.HALF_UP);
    }
}
