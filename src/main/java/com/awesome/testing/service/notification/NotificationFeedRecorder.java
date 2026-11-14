package com.awesome.testing.service.notification;

import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.event.CommerceEvents;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.repository.event.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * With {@code notifications.delivery=feed}, turns every commerce email request into an internal
 * {@code notification.requested} feed event for the notification-service instead of an email.
 * <p>
 * A plain, synchronous {@link EventListener} on purpose (like the feed recorder): the event row joins
 * the transaction that published the {@link CustomerNotificationRequestedEvent}, so it exists if and only
 * if the payment, loyalty credit or gift card that caused it committed. The email path in
 * {@link CommerceNotificationListener} runs after commit instead and stands down in feed mode.
 * <p>
 * Template properties are validated exactly as the email renderer does, before anything is recorded.
 * A request with a {@code requestId} gets an event id derived from it, so a repeated request (a
 * warehouse retry) records nothing new; without one the event id doubles as the {@code requestId}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationFeedRecorder {

    private static final String EVENT_ID_PREFIX = CommerceEventType.NOTIFICATION_REQUESTED.wireName() + ":";

    private final NotificationProperties properties;
    private final CustomerEmailRenderer renderer;
    private final UserRepository userRepository;
    private final DomainEventRepository domainEvents;
    private final CommerceEvents commerceEvents;

    public boolean isActive() {
        return properties.isFeed();
    }

    @EventListener
    public void onCustomerNotificationRequested(CustomerNotificationRequestedEvent event) {
        if (!isActive()) {
            return;
        }
        Optional<UserEntity> user = userRepository.findByUsername(event.username());
        if (user.isEmpty()) {
            log.warn("No user {} for the {} notification; nothing recorded", event.username(), event.template());
            return;
        }
        record(user.get(), event.template(), event.properties(), event.requestId());
    }

    /**
     * Records one {@code notification.requested} event in the current transaction.
     *
     * @param requestId the idempotency key, or null to use the new event's id
     * @return the recorded event, empty when one with the same requestId was already recorded
     */
    public Optional<CommerceEvent> record(UserEntity user, EmailTemplate template, Map<String, String> source,
                                          UUID requestId) {
        Map<String, String> templateProperties = source == null ? Map.of() : source;
        renderer.validate(template, templateProperties);
        UUID eventId = requestId == null ? UUID.randomUUID() : eventIdFor(requestId);
        if (requestId != null && domainEvents.findByEventId(eventId).isPresent()) {
            return Optional.empty();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", requestId == null ? eventId : requestId);
        data.put("template", template.name());
        data.put("recipientEmail", user.getEmail());
        data.put("recipientName", recipientName(user));
        data.put("properties", new TreeMap<>(templateProperties));
        return Optional.of(commerceEvents.publishData(eventId, CommerceEventType.NOTIFICATION_REQUESTED,
                user.getUsername(), null, data));
    }

    static UUID eventIdFor(UUID requestId) {
        return UUID.nameUUIDFromBytes((EVENT_ID_PREFIX + requestId).getBytes(StandardCharsets.UTF_8));
    }

    /** "First Last" when the user has a name, otherwise the username. */
    static String recipientName(UserEntity user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? user.getUsername() : name;
    }
}
