package com.awesome.testing.service.webhook;

import com.awesome.testing.dto.webhook.WebhookEventTypeDto;
import com.awesome.testing.event.CommerceEventType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Event types a subscription may name: the wire name of every commerce event offered to webhooks
 * (internal service events such as {@code card-refund.requested} and
 * {@code notification.requested} are not) plus {@value #PING}.
 */
public final class WebhookEventTypes {

    public static final String PING = "webhook.ping";

    private WebhookEventTypes() {
    }

    public static List<WebhookEventTypeDto> all() {
        List<WebhookEventTypeDto> types = new ArrayList<>();
        for (CommerceEventType type : CommerceEventType.values()) {
            if (!type.offeredToWebhooks()) {
                continue;
            }
            types.add(new WebhookEventTypeDto(type.wireName(), describe(type)));
        }
        types.add(new WebhookEventTypeDto(PING, "Sent by POST /api/v1/webhooks/{id}/test"));
        return types;
    }

    public static Set<String> names() {
        return all().stream().map(WebhookEventTypeDto::name).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Distinct names in request order; throws {@link IllegalArgumentException} naming the first unknown one. */
    public static List<String> normalize(List<String> requested) {
        Set<String> known = names();
        Set<String> result = new LinkedHashSet<>();
        for (String name : requested) {
            String trimmed = name == null ? "" : name.trim();
            if (!known.contains(trimmed)) {
                throw new IllegalArgumentException("Unknown event type '" + trimmed + "'. Allowed: "
                        + String.join(", ", known));
            }
            result.add(trimmed);
        }
        return List.copyOf(result);
    }

    private static String describe(CommerceEventType type) {
        return switch (type) {
            case ORDER_CREATED -> "An order was placed";
            case ORDER_PAID -> "An order was paid";
            case ORDER_CANCELLED -> "An order was cancelled";
            case ORDER_SHIPPED -> "An order was shipped";
            case ORDER_DELIVERED -> "An order was delivered";
            case REFUND_BOOKED -> "A refund was booked for an order";
            case PAYMENT_FAILED -> "A payment attempt failed";
            case CARD_REFUND_REQUESTED, NOTIFICATION_REQUESTED -> throw new IllegalArgumentException(type + " is not offered to webhooks");
        };
    }

    static List<String> wireNames() {
        return Arrays.stream(CommerceEventType.values())
                .filter(CommerceEventType::offeredToWebhooks)
                .map(CommerceEventType::wireName)
                .toList();
    }
}
