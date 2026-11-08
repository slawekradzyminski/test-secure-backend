package com.awesome.testing.service.password;

import com.awesome.testing.config.properties.PasswordResetProperties;
import com.awesome.testing.jms.EmailTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Whether the local email outbox is in use: the {@code local-outbox} transport with
 * {@code password-reset.local-outbox-enabled}. Decided at runtime so the same bean graph serves
 * every profile; when inactive the outbox endpoints answer 404 and stay out of OpenAPI.
 */
@Component
public class LocalEmailOutboxAvailability {

    private final boolean active;

    public LocalEmailOutboxAvailability(PasswordResetProperties passwordResetProperties,
                                        @Value("${app.email.transport:jms}") String transport) {
        this.active = EmailTransport.from(transport) == EmailTransport.LOCAL_OUTBOX
                && passwordResetProperties.isLocalOutboxEnabled();
    }

    public boolean isActive() {
        return active;
    }
}
