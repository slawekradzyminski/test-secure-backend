package com.awesome.testing.service.payment;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Card first, up to what is still refundable on the captured payment; whatever
 * remains is offered to the {@link RefundLegHandler}s in order (gift card,
 * then loyalty points, once the loyalty track registers them). Without handlers
 * a remainder is simply not sent anywhere.
 */
@Component
public class CardFirstRefundSplitter implements RefundSplitter {

    private final List<RefundLegHandler> handlers;

    @Autowired
    public CardFirstRefundSplitter(ObjectProvider<RefundLegHandler> handlers) {
        this(handlers.orderedStream().toList());
    }

    public CardFirstRefundSplitter(List<RefundLegHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    @Override
    public RefundSplit split(RefundSplitContext context) {
        BigDecimal amount = money(context.amount());
        BigDecimal card = amount.min(money(context.cardRefundable())).max(BigDecimal.ZERO);
        BigDecimal remaining = amount.subtract(card);
        List<RefundLeg> legs = new ArrayList<>();
        for (RefundLegHandler handler : handlers) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal taken = handler.allocate(context, remaining);
            taken = taken == null ? BigDecimal.ZERO : money(taken).max(BigDecimal.ZERO).min(remaining);
            if (taken.signum() > 0) {
                legs.add(new RefundLeg(handler.type(), taken));
                remaining = remaining.subtract(taken);
            }
        }
        return new RefundSplit(card, legs);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }
}
