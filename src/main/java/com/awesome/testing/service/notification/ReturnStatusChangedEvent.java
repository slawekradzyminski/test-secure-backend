package com.awesome.testing.service.notification;

/** Published after an administrator decision changes a return request. */
public record ReturnStatusChangedEvent(Long returnId) {
}
