package com.awesome.testing.service.payment;

/**
 * Decides how a booked refund is divided between the card and the other legs.
 * Exactly one bean ({@link CardFirstRefundSplitter}); extend it with
 * {@link RefundLegHandler} beans rather than replacing it.
 */
@FunctionalInterface
public interface RefundSplitter {

    /**
     * @return a split whose card part is between zero and {@code min(amount, cardRefundable)}
     *         and whose total does not exceed {@code amount}
     */
    RefundSplit split(RefundSplitContext context);
}
