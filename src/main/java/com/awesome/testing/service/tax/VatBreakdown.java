package com.awesome.testing.service.tax;

import java.math.BigDecimal;

/**
 * The VAT contained in a VAT-inclusive (gross) amount.
 *
 * @param rate        rate in percent, scale 2
 * @param taxAmount   VAT part of {@code totalAmount}, scale 2
 * @param netAmount   {@code totalAmount - taxAmount}, scale 2
 * @param totalAmount the gross amount, scale 2
 */
public record VatBreakdown(BigDecimal rate, BigDecimal taxAmount, BigDecimal netAmount, BigDecimal totalAmount) {
}
