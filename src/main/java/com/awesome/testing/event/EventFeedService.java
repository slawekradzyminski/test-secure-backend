package com.awesome.testing.event;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.event.EventFeedDto;
import com.awesome.testing.dto.event.FeedEventDto;
import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.repository.event.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the {@code domain_events} feed for service consumers, paged by feed position (V29).
 * Every read first gives committed events their position ({@link FeedPositions}), so an event
 * whose transaction committed late is served after the events that overtook it instead of being
 * skipped by a cursor that already moved past its sequence.
 */
@Service
@RequiredArgsConstructor
public class EventFeedService {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 200;

    private final DomainEventRepository repository;
    private final FeedPositions feedPositions;

    /**
     * Runs without a surrounding transaction: positions are assigned and committed first, then
     * the page is read.
     *
     * @param after  feed position to continue after; null means 0
     * @param limit  page size 1..{@value #MAX_LIMIT}; null means {@value #DEFAULT_LIMIT}
     * @param types  comma-separated wire names; null or blank means every type
     */
    public EventFeedDto read(String after, String limit, String types) {
        long cursor = parseAfter(after);
        int size = parseLimit(limit);
        Set<String> wireNames = parseTypes(types);
        feedPositions.assignCommitted();
        PageRequest page = PageRequest.of(0, size + 1);
        List<DomainEventEntity> rows = wireNames.isEmpty()
                ? repository.findFeed(cursor, page)
                : repository.findFeedOfTypes(cursor, wireNames, page);
        boolean hasMore = rows.size() > size;
        List<FeedEventDto> events = rows.stream().limit(size)
                .map(row -> toDto(row, row.getFeedPosition())).toList();
        long nextCursor = events.isEmpty() ? cursor : events.getLast().sequence();
        return new EventFeedDto(events, nextCursor, hasMore);
    }

    /** The event as the broker relay sends it, numbered by its insert {@code sequence}. */
    public static FeedEventDto toDto(DomainEventEntity row) {
        return toDto(row, row.getSequence());
    }

    private static FeedEventDto toDto(DomainEventEntity row, long sequence) {
        return new FeedEventDto(sequence, row.getEventId(), row.getType(),
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
