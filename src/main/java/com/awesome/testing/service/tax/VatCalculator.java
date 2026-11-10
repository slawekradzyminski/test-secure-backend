package com.awesome.testing.service.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * VAT arithmetic for VAT-inclusive prices. The VAT in a gross amount is
 * {@code gross - gross / (1 + rate / 100)}, computed exactly as
 * {@code gross * rate / (100 + rate)} and rounded half-up to cents once; the
 * net amount is the remainder, so {@code net + tax == gross} always holds.
 */
public final class VatCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private VatCalculator() {
    }

    public static VatBreakdown breakdown(BigDecimal grossAmount, BigDecimal ratePercent) {
        BigDecimal gross = grossAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal rate = ratePercent.setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = taxIn(gross, rate);
        return new VatBreakdown(rate, tax, gross.subtract(tax), gross);
    }

    /** VAT contained in {@code grossAmount} at {@code ratePercent}, half-up to cents. */
    public static BigDecimal taxIn(BigDecimal grossAmount, BigDecimal ratePercent) {
        if (grossAmount.signum() <= 0 || ratePercent.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }
        return grossAmount.multiply(ratePercent).divide(HUNDRED.add(ratePercent), 2, RoundingMode.HALF_UP);
    }
}
