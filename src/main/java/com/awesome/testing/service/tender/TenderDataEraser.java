package com.awesome.testing.service.tender;

import com.awesome.testing.service.giftcard.GiftCardService;
import com.awesome.testing.service.loyalty.LoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes a forgotten user's loyalty account and ledger and cart selection,
 * and drops the user as recipient of gift cards. The cards stay usable: they
 * are bearer instruments.
 */
@Component
@RequiredArgsConstructor
public class TenderDataEraser {

    private final CartTenderService cartTenderService;
    private final LoyaltyService loyaltyService;
    private final GiftCardService giftCardService;

    @Transactional
    public void forget(String username) {
        cartTenderService.clear(username);
        loyaltyService.forget(username);
        giftCardService.forgetRecipient(username);
    }
}
