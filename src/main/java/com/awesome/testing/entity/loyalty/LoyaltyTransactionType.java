package com.awesome.testing.entity.loyalty;

/** Kinds of loyalty ledger rows. */
public enum LoyaltyTransactionType {
    /** Points earned when an order was paid. */
    EARNED,
    /** Points spent at checkout. */
    REDEEMED,
    /** Spent points returned by a cancellation or refund. */
    RESTORED,
    /** Earned points taken back after a cancellation or refund. */
    REVOKED
}
