package com.awesome.testing.event;

import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.entity.event.DomainEventFeedHeadEntity;
import com.awesome.testing.repository.event.DomainEventFeedHeadRepository;
import com.awesome.testing.repository.event.DomainEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedPositionsTest {

    private static final PageRequest BATCH = PageRequest.of(0, FeedPositions.BATCH_SIZE);

    private final DomainEventRepository events = mock(DomainEventRepository.class);
    private final DomainEventFeedHeadRepository heads = mock(DomainEventFeedHeadRepository.class);
    private final FeedPositions positions = new FeedPositions(events, heads);

    @Test
    void doesNotLockTheHeadWhenEveryEventHasItsPosition() {
        when(events.existsByFeedPositionIsNull()).thenReturn(false);

        positions.assignCommitted();

        verify(heads, never()).findForUpdate(any());
    }

    @Test
    void inOrderCommitsKeepTheirSequenceAsPosition() {
        DomainEventFeedHeadEntity head = head(10);
        List<DomainEventEntity> rows = rows(11, 12, 13);
        when(events.existsByFeedPositionIsNull()).thenReturn(true);
        when(events.findUnpositioned(BATCH)).thenReturn(rows);

        positions.assignCommitted();

        assertThat(rows).extracting(DomainEventEntity::getFeedPosition).containsExactly(11L, 12L, 13L);
        assertThat(head.getLastPosition()).isEqualTo(13L);
    }

    @Test
    void aLateCommitIsPositionedAboveEverythingAlreadyServed() {
        // Sequence 534 committed after 536 was positioned (and maybe served) as 536.
        DomainEventFeedHeadEntity head = head(536);
        List<DomainEventEntity> rows = rows(534, 535, 537);
        when(events.existsByFeedPositionIsNull()).thenReturn(true);
        when(events.findUnpositioned(BATCH)).thenReturn(rows);

        positions.assignCommitted();

        assertThat(rows).extracting(DomainEventEntity::getFeedPosition).containsExactly(537L, 538L, 539L);
        assertThat(head.getLastPosition()).isEqualTo(539L);
    }

    @Test
    void positionsEveryBatchUntilAShortOne() {
        DomainEventFeedHeadEntity head = head(0);
        List<DomainEventEntity> full = rows(LongStream.rangeClosed(1, FeedPositions.BATCH_SIZE).toArray());
        List<DomainEventEntity> rest = rows(FeedPositions.BATCH_SIZE + 1L);
        when(events.existsByFeedPositionIsNull()).thenReturn(true);
        when(events.findUnpositioned(BATCH)).thenReturn(full, rest);

        positions.assignCommitted();

        assertThat(rest.getFirst().getFeedPosition()).isEqualTo(FeedPositions.BATCH_SIZE + 1L);
        assertThat(head.getLastPosition()).isEqualTo(FeedPositions.BATCH_SIZE + 1L);
    }

    @Test
    void createsAMissingHeadAfterTheHighestPosition() {
        when(events.existsByFeedPositionIsNull()).thenReturn(true);
        when(heads.findForUpdate(FeedPositions.HEAD_ID)).thenReturn(Optional.empty());
        when(events.maxFeedPosition()).thenReturn(7L);
        when(heads.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        List<DomainEventEntity> rows = rows(3);
        when(events.findUnpositioned(BATCH)).thenReturn(rows);

        positions.assignCommitted();

        assertThat(rows.getFirst().getFeedPosition()).isEqualTo(8L);
    }

    private DomainEventFeedHeadEntity head(long lastPosition) {
        DomainEventFeedHeadEntity head = new DomainEventFeedHeadEntity(FeedPositions.HEAD_ID, lastPosition);
        when(heads.findForUpdate(FeedPositions.HEAD_ID)).thenReturn(Optional.of(head));
        return head;
    }

    private static List<DomainEventEntity> rows(long... sequences) {
        List<DomainEventEntity> rows = new ArrayList<>();
        for (long sequence : sequences) {
            rows.add(DomainEventEntity.builder().sequence(sequence).eventId(UUID.randomUUID()).type("order.created")
                    .occurredAt(Instant.EPOCH).recordedAt(Instant.EPOCH).payload("{}").build());
        }
        return rows;
    }
}
