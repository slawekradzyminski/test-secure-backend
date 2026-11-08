package com.awesome.testing.service.notification;

/** Published when a product's stock moves from zero to a positive quantity. */
public record StockReplenishedEvent(Long productId) {
}
