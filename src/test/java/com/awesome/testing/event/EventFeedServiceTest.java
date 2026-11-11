package com.awesome.testing.event;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.event.EventFeedDto;
import com.awesome.testing.dto.event.FeedEventDto;
import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.repository.event.DomainEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventFeedServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final Instant SETTLED_BEFORE = Instant.parse("2026-11-10T11:59:57Z");

    private final DomainEventRepository repository = mock(DomainEventRepository.class);
    private final EventFeedService service =
            new EventFeedService(repository, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(3));

    @Test
    void defaultsReadFromTheStartWithTheDefaultPageOfSettledEvents() {
        when(repository.findSettled(0L, SETTLED_BEFORE, PageRequest.of(0, 101))).thenReturn(rows(1, 2));

        EventFeedDto feed = service.read(null, null, null);

        assertThat(feed.events()).extracting(FeedEventDto::sequence).containsExactly(1L, 2L);
        assertThat(feed.nextCursor()).isEqualTo(2L);
        assertThat(feed.hasMore()).isFalse();
    }

    @Test
    void oneRowBeyondTheLimitMeansMoreAndIsNotReturned() {
        when(repository.findSettled(5L, SETTLED_BEFORE, PageRequest.of(0, 3))).thenReturn(rows(6, 7, 8));

        EventFeedDto feed = service.read("5", "2", "");

        assertThat(feed.events()).extracting(FeedEventDto::sequence).containsExactly(6L, 7L);
        assertThat(feed.nextCursor()).isEqualTo(7L);
        assertThat(feed.hasMore()).isTrue();
    }

    @Test
    void anExactlyFullPageHasNoMore() {
        when(repository.findSettled(0L, SETTLED_BEFORE, PageRequest.of(0, 3))).thenReturn(rows(1, 2));

        assertThat(service.read("0", "2", null).hasMore()).isFalse();
    }

    @Test
    void anEmptyPageKeepsTheCursor() {
        when(repository.findSettled(42L, SETTLED_BEFORE, PageRequest.of(0, 201))).thenReturn(List.of());

        EventFeedDto feed = service.read(" 42 ", "200", null);

        assertThat(feed.events()).isEmpty();
        assertThat(feed.nextCursor()).isEqualTo(42L);
        assertThat(feed.hasMore()).isFalse();
    }

    @Test
    void typesFilterByDistinctTrimmedWireNames() {
        when(repository.findSettledOfTypes(0L, SETTLED_BEFORE, Set.of("order.cancelled", "card-refund.requested"),
                PageRequest.of(0, 2))).thenReturn(rows(4));

        EventFeedDto feed = service.read(null, "1", "order.cancelled, card-refund.requested,order.cancelled");

        assertThat(feed.events()).singleElement().extracting(FeedEventDto::sequence).isEqualTo(4L);
        verify(repository, never()).findSettled(anyLong(), any(), any());
    }

    @Test
    void mapsRowsToTheWireShape() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        DomainEventEntity row = DomainEventEntity.builder().sequence(3L).eventId(id).type("order.paid").orderId(8L)
                .username("buyer").occurredAt(Instant.parse("2026-11-10T11:00:00.120Z")).recordedAt(NOW)
                .payload("{\"orderId\":8}").requestId("req-8").traceId("4bf92f3577b34da6a3ce929d0e0e4736").build();

        assertThat(EventFeedService.toDto(row)).isEqualTo(new FeedEventDto(3L, id, "order.paid",
                "2026-11-10T11:00:00.120Z", 8L, "buyer", "{\"orderId\":8}", "req-8",
                "4bf92f3577b34da6a3ce929d0e0e4736"));
    }

    @Test
    void parsesTheCursor() {
        assertThat(EventFeedService.parseAfter(null)).isZero();
        assertThat(EventFeedService.parseAfter("0")).isZero();
        assertThat(EventFeedService.parseAfter("17")).isEqualTo(17L);
        for (String bad : new String[] {"-1", "abc", "", "1.5", "99999999999999999999"}) {
            assertBadRequest(() -> EventFeedService.parseAfter(bad), "after must be a non-negative integer");
        }
    }

    @Test
    void parsesTheLimit() {
        assertThat(EventFeedService.parseLimit(null)).isEqualTo(100);
        assertThat(EventFeedService.parseLimit("1")).isEqualTo(1);
        assertThat(EventFeedService.parseLimit("200")).isEqualTo(200);
        for (String bad : new String[] {"0", "201", "-5", "ten", ""}) {
            assertBadRequest(() -> EventFeedService.parseLimit(bad), "limit must be between 1 and 200");
        }
    }

    @Test
    void rejectsUnknownOrEmptyTypes() {
        assertThat(EventFeedService.parseTypes(null)).isEmpty();
        assertThat(EventFeedService.parseTypes("  ")).isEmpty();
        assertThat(EventFeedService.parseTypes(" order.paid,card-refund.requested,order.paid"))
                .containsExactly("order.paid", "card-refund.requested");
        assertBadRequest(() -> EventFeedService.parseTypes("order.created,ORDER_PAID"), "Unknown event type 'ORDER_PAID'");
        assertBadRequest(() -> EventFeedService.parseTypes("order.created,"), "Unknown event type ''");
        assertBadRequest(() -> EventFeedService.parseTypes("webhook.ping"), "Unknown event type 'webhook.ping'");
    }

    private static void assertBadRequest(Runnable call, String message) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo(message);
                });
    }

    private static List<DomainEventEntity> rows(long... sequences) {
        return LongStream.of(sequences).mapToObj(sequence -> DomainEventEntity.builder().sequence(sequence)
                .eventId(UUID.randomUUID()).type("order.created").occurredAt(NOW).recordedAt(NOW).payload("{}").build())
                .toList();
    }
}
