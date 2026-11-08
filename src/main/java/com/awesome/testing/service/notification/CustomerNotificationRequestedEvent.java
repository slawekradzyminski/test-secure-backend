package com.awesome.testing.service.notification;

import com.awesome.testing.dto.email.EmailTemplate;

import java.util.Map;

/** A shipment or return email requested by warehouse-service for a known customer. */
public record CustomerNotificationRequestedEvent(
        String username, EmailTemplate template, String subject, String message, Map<String, String> properties) {
}
