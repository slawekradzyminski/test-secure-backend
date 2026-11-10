package com.awesome.testing.service.payment;

import java.math.BigDecimal;

/** A non-card part of a refund. */
public record RefundLeg(RefundLegType type, BigDecimal amount) {
}
