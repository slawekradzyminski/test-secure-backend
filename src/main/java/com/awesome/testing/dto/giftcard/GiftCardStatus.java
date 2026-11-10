package com.awesome.testing.dto.giftcard;

/**
 * Effective state of a gift card, derived in this order: a disabled card is
 * DISABLED, then a card past {@code expiresAt} is EXPIRED, then a card without
 * balance is REDEEMED, otherwise ACTIVE.
 */
public enum GiftCardStatus {
    ACTIVE,
    REDEEMED,
    EXPIRED,
    DISABLED
}
