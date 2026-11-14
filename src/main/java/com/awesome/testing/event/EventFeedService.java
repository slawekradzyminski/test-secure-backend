package com.awesome.testing.event;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.event.EventFeedDto;
import com.awesome.testing.dto.event.FeedEventDto;
import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.repository.event.DomainEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the {@code domain_events} feed for service consumers. Only events recorded at least
 * {@code events.settle-delay} ago are returned, so a transaction that took a lower sequence but
 * committed later is not skipped by a consumer that already moved its cursor past it.
 */
@Service
public class EventFeedService {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 200;

    private final DomainEventRepository repository;
    private final Clock commerceClock;
    private final Duration settleDelay;

    public EventFeedService(DomainEventRepository repository, Clock commerceClock,
                            @Value("${events.settle-delay:PT3S}") Duration settleDelay) {
        this.repository = repository;
        this.commerceClock = commerceClock;
        this.settleDelay = settleDelay;
    }

    /**
     * @param after  sequence to continue after; null means 0
     * @param limit  page size 1..{@value #MAX_LIMIT}; null means {@value #DEFAULT_LIMIT}
     * @param types  comma-separated wire names; null or blank means every type
     */
    @Transactional(readOnly = true)
    public EventFeedDto read(String after, String limit, String types) {
        long cursor = parseAfter(after);
        int size = parseLimit(limit);
        Set<String> wireNames = parseTypes(types);
        Instant settledBefore = commerceClock.instant().minus(settleDelay);
        PageRequest page = PageRequest.of(0, size + 1);
        List<DomainEventEntity> rows = wireNames.isEmpty()
                ? repository.findSettled(cursor, settledBefore, page)
                : repository.findSettledOfTypes(cursor, settledBefore, wireNames, page);
        boolean hasMore = rows.size() > size;
        List<FeedEventDto> events = rows.stream().limit(size).map(EventFeedService::toDto).toList();
        long nextCursor = events.isEmpty() ? cursor : events.getLast().sequence();
        return new EventFeedDto(events, nextCursor, hasMore);
    }

    static FeedEventDto toDto(DomainEventEntity row) {
        return new FeedEventDto(row.getSequence(), row.getEventId(), row.getType(),
                DateTimeFormatter.ISO_INSTANT.format(row.getOccurredAt()), row.getOrderId(), row.getUsername(),
                row.getPayload(), row.getRequestId(), row.getTraceId(), row.getSpanId());
    }

    static long parseAfter(String after) {
        if (after == null) {
            return 0;
        }
        long value;
        try {
            value = Long.parseLong(after.trim());
        } catch (NumberFormatException e) {
            throw badRequest("after must be a non-negative integer", e);
        }
        if (value < 0) {
            throw badRequest("after must be a non-negative integer");
        }
        return value;
    }

    static int parseLimit(String limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        int value;
        try {
            value = Integer.parseInt(limit.trim());
        } catch (NumberFormatException e) {
            throw badRequest(limitMessage(), e);
        }
        if (value < 1 || value > MAX_LIMIT) {
            throw badRequest(limitMessage());
        }
        return value;
    }

    static Set<String> parseTypes(String types) {
        Set<String> result = new LinkedHashSet<>();
        if (types == null || types.isBlank()) {
            return result;
        }
        for (String part : types.split(",", -1)) {
            String name = part.trim();
            if (CommerceEventType.fromWireName(name).isEmpty()) {
                throw badRequest("Unknown event type '" + name + "'");
            }
            result.add(name);
        }
        return result;
    }

    private static String limitMessage() {
        return "limit must be between 1 and " + MAX_LIMIT;
    }

    private static CustomException badRequest(String message) {
        return new CustomException(message, HttpStatus.BAD_REQUEST);
    }

    private static CustomException badRequest(String message, Throwable cause) {
        return new CustomException(message, HttpStatus.BAD_REQUEST, cause);
    }
}
