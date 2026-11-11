package com.awesome.testing.service.payment;

import com.awesome.testing.entity.payment.CardRefundRequestEntity;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.event.CommerceEvents;
import com.awesome.testing.repository.payment.CardRefundRequestRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CardRefundsTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID PAYMENT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    private final CardRefundRequestRepository repository = mock(CardRefundRequestRepository.class);
    private final CommerceEvents commerceEvents = mock(CommerceEvents.class);
    private final CardRefunds cardRefunds = new CardRefunds(repository, commerceEvents, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @SuppressWarnings("unchecked")
    void remembersTheRequestAndPublishesExactlyTheRefundData() {
        cardRefunds.request(4L, "buyer", PAYMENT, REQUEST, new BigDecimal("9.99"), "Return #2");

        ArgumentCaptor<CardRefundRequestEntity> saved = ArgumentCaptor.forClass(CardRefundRequestEntity.class);
        ArgumentCaptor<Map<String, ?>> data = ArgumentCaptor.forClass(Map.class);
        var order = inOrder(repository, commerceEvents);
        order.verify(repository).save(saved.capture());
        order.verify(commerceEvents).publishData(eq(CommerceEventType.CARD_REFUND_REQUESTED), eq("buyer"), eq(4L),
                data.capture());
        assertThat(saved.getValue()).usingRecursiveComparison().isEqualTo(CardRefundRequestEntity.builder()
                .requestId(REQUEST).orderId(4L).paymentId(PAYMENT).amount(new BigDecimal("9.99"))
                .reason("Return #2").createdAt(NOW).build());
        assertThat(data.getValue().keySet()).containsExactly("requestId", "orderId", "amount", "reason");
        assertThat((Map<String, Object>) data.getValue()).containsEntry("requestId", REQUEST)
                .containsEntry("orderId", 4L)
                .containsEntry("amount", new BigDecimal("9.99"))
                .containsEntry("reason", "Return #2");
    }

    @Test
    void anUnknownReasonIsPublishedAsNull() {
        cardRefunds.request(4L, "buyer", PAYMENT, REQUEST, BigDecimal.ONE, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, ?>> data = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(commerceEvents).publishData(eq(CommerceEventType.CARD_REFUND_REQUESTED), eq("buyer"),
                eq(4L), data.capture());
        assertThat(data.getValue()).containsEntry("reason", null);
    }

    @Test
    void readsTheLedger() {
        when(repository.sumAmountByOrderId(4L)).thenReturn(new BigDecimal("15.00"));
        when(repository.existsById(REQUEST)).thenReturn(true);

        assertThat(cardRefunds.cardRefunded(4L)).isEqualByComparingTo("15.00");
        assertThat(cardRefunds.isRequested(REQUEST)).isTrue();
        assertThat(cardRefunds.isRequested(PAYMENT)).isFalse();
    }
}
