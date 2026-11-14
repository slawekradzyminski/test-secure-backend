package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.payment.PaymentResultResponseDto;
import com.awesome.testing.entity.EmailEventEntity;
import com.awesome.testing.repository.EmailEventRepository;
import com.awesome.testing.repository.event.DomainEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The default {@code notifications.delivery=email}: commerce emails are sent here and nothing goes to the feed. */
class NotificationEmailDeliveryTest extends AbstractPaymentFlowTest {

    @Autowired
    private DomainEventRepository domainEvents;

    @Autowired
    private EmailEventRepository emailEventRepository;

    @Test
    void aCapturedPaymentIsEmailedAndRecordsNoNotificationRequestedEvent() {
        // given
        long before = domainEvents.maxSequence();
        OrderDto order = order(clientToken, product("9.00", 5), 1);

        // when
        postResult(order.getId(), captured(UUID.randomUUID(), UUID.randomUUID(), "9.00"), PaymentResultResponseDto.class);
        ResponseEntity<String> feed = executeGet("/internal/v1/events?limit=200&types=notification.requested&after="
                + before, serviceHeaders("test-notification-token"), String.class);

        // then
        assertThat(feed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(feed.getBody()).doesNotContain(client.getUsername());
        assertThat(emailEventRepository.findAll()).filteredOn(event -> client.getEmail().equals(event.getRecipientEmail()))
                .extracting(EmailEventEntity::getType)
                .contains(EmailTemplate.PAYMENT_RECEIVED, EmailTemplate.LOYALTY_POINTS_EARNED);
    }
}
