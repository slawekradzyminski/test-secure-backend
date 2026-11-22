package com.awesome.testing.event;

import com.awesome.testing.entity.event.DomainEventEntity;
import com.awesome.testing.entity.event.DomainEventFeedHeadEntity;
import com.awesome.testing.repository.event.DomainEventFeedHeadRepository;
import com.awesome.testing.repository.event.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gives committed {@code domain_events} rows their feed position (V29) in commit order.
 *
 * <p>{@code sequence} is taken when a row is inserted, so a transaction that inserts an event and
 * commits late is overtaken by a later one, and a consumer cursor could pass the late event before
 * it became visible (exploratory chaos session, finding F1). Positions are given only to rows this
 * transaction can see, which are committed, under the lock of the single feed head row. A row that
 * commits later is positioned by a later run, above everything already served. Each position is
 * {@code max(last + 1, sequence)}, so it equals the sequence whenever commits follow insert order.
 */
@Component
@RequiredArgsConstructor
public class FeedPositions {

    static final int HEAD_ID = 1;
    static final int BATCH_SIZE = 500;

    private final DomainEventRepository events;
    private final DomainEventFeedHeadRepository heads;

    /** Must run in its own transaction, committed before the feed is read. */
    @Transactional
    public void assignCommitted() {
        if (!events.existsByFeedPositionIsNull()) {
            return;
        }
        DomainEventFeedHeadEntity head = heads.findForUpdate(HEAD_ID).orElseGet(this::createHead);
        long last = head.getLastPosition();
        List<DomainEventEntity> rows;
        do {
            rows = events.findUnpositioned(PageRequest.of(0, BATCH_SIZE));
            for (DomainEventEntity row : rows) {
                last = Math.max(last + 1, row.getSequence());
                row.setFeedPosition(last);
            }
            events.flush();
        } while (rows.size() == BATCH_SIZE);
        head.setLastPosition(last);
    }

    /** V29 inserts the head row on PostgreSQL; the H2 schema of the unit tests starts without one. */
    private DomainEventFeedHeadEntity createHead() {
        return heads.saveAndFlush(new DomainEventFeedHeadEntity(HEAD_ID, events.maxFeedPosition()));
    }
}
