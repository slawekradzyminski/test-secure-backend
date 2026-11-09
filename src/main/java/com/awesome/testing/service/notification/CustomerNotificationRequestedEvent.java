package com.awesome.testing.service.notification;

import com.awesome.testing.dto.email.EmailTemplate;

import java.util.Map;
import java.util.UUID;

/** A shipment or return email requested by warehouse-service for a known customer. */
public record CustomerNotificationRequestedEvent(
        UUID requestId, String username, EmailTemplate template, Map<String, String> properties) {
}
