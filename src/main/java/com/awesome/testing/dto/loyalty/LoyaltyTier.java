package com.awesome.testing.dto.loyalty;

/**
 * Informational tier derived from the points earned over the account's
 * lifetime: SILVER from 1000 points, GOLD from 5000. Tiers do not change the
 * earn or redeem rates.
 */
public enum LoyaltyTier {
    BRONZE,
    SILVER,
    GOLD
}
