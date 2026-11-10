package com.awesome.testing.service.tender;

import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.entity.tender.CartTenderEntity;
import com.awesome.testing.service.checkout.CartSummaryContext;
import com.awesome.testing.service.checkout.CartSummaryContributor;
import com.awesome.testing.service.giftcard.GiftCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Previews the gift card and points in the cart summary. Without either the
 * cart is left untouched, so its JSON keeps its legacy shape. A card that
 * cannot pay (disabled, expired, empty, deleted) previews as 0.00; checkout
 * then rejects it.
 */
@Component
@Order(TenderCheckoutContributor.ORDER)
@RequiredArgsConstructor
public class TenderCartSummaryContributor implements CartSummaryContributor {

    private final CartTenderService cartTenderService;
    private final GiftCardService giftCardService;

    @Override
    public void contribute(CartSummaryContext context) {
        Optional<CartTenderEntity> selection = cartTenderService.selection(context.username());
        if (selection.isEmpty()) {
            return;
        }
        String code = selection.get().getGiftCardCode();
        BigDecimal giftCardBalance = code == null ? null : giftCardService.findByCode(code)
                .map(giftCardService::usableBalance)
                .orElse(BigDecimal.ZERO);
        TenderSplit split = TenderSplit.split(
                context.subtotal().subtract(context.discount()), giftCardBalance, selection.get().getPoints());
        CartDto cart = context.cart();
        cart.setGiftCardCode(code);
        cart.setGiftCardAmount(split.giftCardAmount());
        cart.setPointsRedeemed(split.pointsRedeemed());
        cart.setPointsAmount(split.pointsAmount());
        cart.setAmountDue(split.amountDue());
    }
}
