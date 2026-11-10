package com.awesome.testing.service.tender;

import java.math.BigDecimal;

/**
 * What a refund or cancellation gave back to the order's tenders.
 *
 * @param giftCardAmount put back on the gift card
 * @param points         put back on the loyalty account
 * @param pointsAmount   value of {@code points}
 * @param unallocated    part of the requested amount neither tender could take
 *                       (more than the order paid with them)
 */
public record TenderRefund(BigDecimal giftCardAmount, long points, BigDecimal pointsAmount, BigDecimal unallocated) {
}
