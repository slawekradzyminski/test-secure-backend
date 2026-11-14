package com.awesome.testing.service.notification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** How commerce emails leave the backend ({@code notifications.*}). Read at runtime: one native bean graph. */
@ConfigurationProperties(prefix = "notifications")
@Getter
@Setter
public class NotificationProperties {

    public enum Delivery {
        /** Render and queue commerce emails in the backend (the default and the old behaviour). */
        EMAIL,
        /** Record an internal {@code notification.requested} feed event for the notification-service instead. */
        FEED
    }

    private Delivery delivery = Delivery.EMAIL;

    public boolean isFeed() {
        return delivery == Delivery.FEED;
    }
}
