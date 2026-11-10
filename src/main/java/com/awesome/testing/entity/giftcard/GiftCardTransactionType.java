package com.awesome.testing.entity.giftcard;

/** Kinds of gift card ledger rows. */
public enum GiftCardTransactionType {
    /** The card was created with its initial amount. */
    ISSUED,
    /** Checkout paid part of an order with the card. */
    REDEEMED,
    /** A cancellation or refund returned money to the card. */
    RESTORED,
    /** An administrator disabled the card; the balance is kept but cannot be spent. */
    DISABLED
}
