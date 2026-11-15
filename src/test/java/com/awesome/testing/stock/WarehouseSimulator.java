package com.awesome.testing.stock;

import com.awesome.testing.dto.stock.StockLevelDto;
import com.awesome.testing.dto.stock.StockLevelsResultDto;
import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.event.CommerceEventType;
import com.awesome.testing.service.stock.StockProjection;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays warehouse-service's side of a cancellation in tests: it keeps every committed
 * {@code stock.restore-requested} event and, when a test asks, restores the stock in the
 * fake ledger and pushes the new levels into the projection, as warehouse-service does a few
 * seconds after the cancel (contract section 3.1).
 */
public class WarehouseSimulator {

    private final StockProjection projection;
    private final FakeStockLedger ledger;
    private final Map<Long, CommerceEvent> pending = new ConcurrentHashMap<>();

    public WarehouseSimulator(StockProjection projection, FakeStockLedger ledger) {
        this.projection = projection;
        this.ledger = ledger;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void record(CommerceEvent event) {
        if (event.type() == CommerceEventType.STOCK_RESTORE_REQUESTED) {
            pending.put(event.orderId(), event);
        }
    }

    /**
     * Forgets pending restores. Order ids repeat once another Spring context recreates the shared
     * H2 schema, so each test starts from an empty record.
     */
    public void clear() {
        pending.clear();
    }

    /** True while the cancel of {@code orderId} carries a stock restore warehouse-service has not applied. */
    public boolean hasPendingRestore(Long orderId) {
        return pending.containsKey(orderId);
    }

    /** The data of the {@code stock.restore-requested} event of {@code orderId}, or null. */
    public Object pendingRestore(Long orderId) {
        CommerceEvent event = pending.get(orderId);
        return event == null ? null : event.data();
    }

    /** Restores the stock of the cancelled orders and pushes the levels; returns how many orders. */
    public int restoreCancelledStock(Long... orderIds) {
        int restored = 0;
        for (Long orderId : orderIds) {
            CommerceEvent event = pending.remove(orderId);
            if (event == null) {
                continue;
            }
            Map<?, ?> restore = event.data();
            Map<Long, Integer> lines = new TreeMap<>();
            for (Object line : (List<?>) restore.get("lines")) {
                Map<?, ?> values = (Map<?, ?>) line;
                lines.merge(((Number) values.get("productId")).longValue(),
                        ((Number) values.get("quantity")).intValue(), Integer::sum);
            }
            Map<Long, FakeStockLedger.Item> levels = ledger.restore(orderId,
                    (String) restore.get("actor"), lines);
            push(levels);
            restored++;
        }
        return restored;
    }

    /** Pushes ledger levels into the projection like warehouse-service's level dispatcher. */
    public StockLevelsResultDto push(Map<Long, FakeStockLedger.Item> levels) {
        return projection.applyLevels(levels.entrySet().stream()
                .map(entry -> new StockLevelDto(entry.getKey(), entry.getValue().quantity(), entry.getValue().version()))
                .toList());
    }
}
