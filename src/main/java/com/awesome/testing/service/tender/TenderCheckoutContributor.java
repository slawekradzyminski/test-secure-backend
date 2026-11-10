package com.awesome.testing.service.tender;

import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.giftcard.GiftCardEntity;
import com.awesome.testing.entity.tender.CartTenderEntity;
import com.awesome.testing.repository.tender.CartTenderRepository;
import com.awesome.testing.service.checkout.CheckoutContext;
import com.awesome.testing.service.checkout.CheckoutContributor;
import com.awesome.testing.service.giftcard.GiftCardService;
import com.awesome.testing.service.loyalty.LoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Pays part of the order with the cart's gift card and loyalty points. Runs
 * after the other contributors so it sees the final {@code totalAmount}. It
 * locks the card and the account, validates them again (any failure is a 409
 * and the whole checkout rolls back, keeping the cart), debits them, records
 * the amounts on the order and clears the cart selection. The ledger rows are
 * written by {@link TenderOrderEvents} once the order has an id.
 */
@Component
@Order(TenderCheckoutContributor.ORDER)
@RequiredArgsConstructor
public class TenderCheckoutContributor implements CheckoutContributor {

    /** Late, so VAT or other contributors run first. */
    public static final int ORDER = 1000;

    private final CartTenderRepository cartTenderRepository;
    private final GiftCardService giftCardService;
    private final LoyaltyService loyaltyService;

    @Override
    public void contribute(CheckoutContext context) {
        Optional<CartTenderEntity> found = cartTenderRepository.findByUsernameForUpdate(context.username());
        if (found.isEmpty()) {
            return;
        }
        CartTenderEntity selection = found.get();
        OrderEntity order = context.order();
        GiftCardEntity card = selection.getGiftCardCode() == null
                ? null : giftCardService.lockForCheckout(selection.getGiftCardCode());
        TenderSplit split = TenderSplit.split(
                order.getTotalAmount(), card == null ? null : card.getBalance(), selection.getPoints());
        if (split.pointsRedeemed() != null && split.pointsRedeemed() > 0) {
            loyaltyService.debitForCheckout(context.username(), split.pointsRedeemed());
            order.setPointsRedeemed(split.pointsRedeemed());
            order.setPointsAmount(split.pointsAmount());
        }
        if (card != null && split.giftCardAmount().signum() > 0) {
            giftCardService.debit(card, split.giftCardAmount());
            order.setGiftCardCode(card.getCode());
            order.setGiftCardAmount(split.giftCardAmount());
        }
        cartTenderRepository.delete(selection);
    }
}
