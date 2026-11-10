package com.awesome.testing.service.giftcard;

import org.springframework.http.HttpStatus;

/** Why a gift card cannot be used right now. */
public record GiftCardFailure(HttpStatus status, String message) {
}
