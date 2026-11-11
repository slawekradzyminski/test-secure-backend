package com.awesome.testing.dto.event;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * A page of the internal event feed, ordered by {@code sequence} ascending.
 *
 * @param nextCursor pass as {@code after} to continue: the last returned sequence, or the
 *                   requested {@code after} when the page is empty
 * @param hasMore    true when further settled events matched beyond this page
 */
@JsonPropertyOrder({"events", "nextCursor", "hasMore"})
public record EventFeedDto(List<FeedEventDto> events, long nextCursor, boolean hasMore) {
}
