package com.awesome.testing.service.notification;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.event.CommerceEvents;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.repository.event.DomainEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationFeedRecorderTest {

    private final NotificationProperties properties = new NotificationProperties();
    private final UserRepository users = mock(UserRepository.class);
    private final DomainEventRepository domainEvents = mock(DomainEventRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final NotificationFeedRecorder recorder = new NotificationFeedRecorder(properties, new CustomerEmailRenderer(),
            users, domainEvents,
            new CommerceEvents(publisher, Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC)));

    @Test
    void emailModeIgnoresNotificationRequests() {
        recorder.onCustomerNotificationRequested(request(UUID.randomUUID()));

        assertThat(recorder.isActive()).isFalse();
        verifyNoInteractions(users, domainEvents, publisher);
    }

    @Test
    void feedModeRecordsTheRequestWithAnEventIdDerivedFromTheRequestId() {
        properties.setDelivery(NotificationProperties.Delivery.FEED);
        UUID requestId = UUID.randomUUID();
        when(users.findByUsername("buyer")).thenReturn(Optional.of(user(" Ada ", "Lovelace")));

        recorder.onCustomerNotificationRequested(request(requestId));

        verify(publisher).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(published -> {
            CommerceEvent event = (CommerceEvent) published;
            return event.type() == CommerceEventType.NOTIFICATION_REQUESTED
                    && event.id().equals(UUID.nameUUIDFromBytes(("notification.requested:" + requestId)
                    .getBytes(StandardCharsets.UTF_8)))
                    && event.username().equals("buyer")
                    && event.orderId() == null
                    && event.data().equals(Map.of("requestId", requestId, "template", "ORDER_DELIVERED",
                    "recipientEmail", "buyer@example.com", "recipientName", "Ada Lovelace",
                    "properties", Map.of("orderId", "7")));
        }));
    }

    @Test
    void aRequestIdThatWasAlreadyRecordedRecordsNothing() {
        properties.setDelivery(NotificationProperties.Delivery.FEED);
        UUID requestId = UUID.randomUUID();
        when(users.findByUsername("buyer")).thenReturn(Optional.of(user("Ada", "Lovelace")));
        when(domainEvents.findByEventId(NotificationFeedRecorder.eventIdFor(requestId)))
                .thenReturn(Optional.of(new DomainEventEntity()));

        recorder.onCustomerNotificationRequested(request(requestId));

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void anUnknownUserRecordsNothing() {
        properties.setDelivery(NotificationProperties.Delivery.FEED);
        when(users.findByUsername("buyer")).thenReturn(Optional.empty());

        recorder.onCustomerNotificationRequested(request(UUID.randomUUID()));

        verifyNoInteractions(publisher);
    }

    @Test
    void invalidTemplatePropertiesAreRejectedBeforeAnythingIsRecorded() {
        assertThatThrownBy(() -> recorder.record(user("Ada", "Lovelace"), EmailTemplate.ORDER_SHIPPED,
                Map.of("orderId", "1"), UUID.randomUUID()))
                .isInstanceOf(CustomException.class)
                .hasMessage("properties.carrier is required for ORDER_SHIPPED");
        assertThatThrownBy(() -> recorder.record(user("Ada", "Lovelace"), EmailTemplate.PASSWORD_RESET_REQUESTED,
                Map.of(), UUID.randomUUID()))
                .isInstanceOf(CustomException.class);
        verifyNoInteractions(domainEvents, publisher);
    }

    @Test
    void withoutARequestIdTheEventIdIsTheRequestId() {
        CommerceEvent event = recorder.record(user("Ada", "Lovelace"), EmailTemplate.BACK_IN_STOCK,
                Map.of("productId", "1", "productName", "Mug"), null).orElseThrow();

        assertThat(event.data().get("requestId")).isEqualTo(event.id());
        verifyNoInteractions(domainEvents);
    }

    @Test
    void theRecipientNameFallsBackToTheUsername() {
        assertThat(NotificationFeedRecorder.recipientName(user("Ada", null))).isEqualTo("Ada");
        assertThat(NotificationFeedRecorder.recipientName(user(null, "Lovelace"))).isEqualTo("Lovelace");
        assertThat(NotificationFeedRecorder.recipientName(user(" ", null))).isEqualTo("buyer");
    }

    private static CustomerNotificationRequestedEvent request(UUID requestId) {
        return new CustomerNotificationRequestedEvent(requestId, "buyer", EmailTemplate.ORDER_DELIVERED,
                Map.of("orderId", "7"));
    }

    private static UserEntity user(String firstName, String lastName) {
        UserEntity user = new UserEntity();
        user.setUsername("buyer");
        user.setEmail("buyer@example.com");
        user.setFirstName(firstName);
        user.setLastName(lastName);
        return user;
    }
}
