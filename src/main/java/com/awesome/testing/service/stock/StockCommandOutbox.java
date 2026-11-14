package com.awesome.testing.service.stock;

import com.awesome.testing.correlation.CorrelationIds;
import com.awesome.testing.correlation.CorrelationTracing;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.stock.StockCommandEntity;
import com.awesome.testing.repository.stock.StockCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.UUID;

/**
 * Records stock commands for warehouse-service in the transaction of the product change
 * (contract sections 4.3 and 4.4). The rows are sent after commit by {@link StockCommandDispatcher},
 * which retries them until warehouse-service accepts them; the public response never waits.
 */
@Component
@RequiredArgsConstructor
public class StockCommandOutbox {

    /** Transaction resource marking that this transaction already asks for a dispatch after commit. */
    private static final Object DISPATCH_REGISTERED = new Object();
    static final String SUPERSEDED = "Superseded by DELETE";

    private final StockCommandRepository repository;
    private final StockCommandDispatcher dispatcher;
    private final CorrelationTracing tracing;
    private final Clock commerceClock;

    /**
     * Section 2.5 for a product created by a request (REST, GraphQL, CSV): the caller sends the
     * returned row with {@link StockCommandDispatcher#sendNow} after commit, before it answers, so
     * the product can be ordered as soon as the response arrives. No background dispatch is asked
     * for, so nothing races the caller for the row; a failed send is retried by the scheduled run.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockCommandEntity initialStock(ProductEntity product, String actor) {
        return save(StockCommandEntity.Command.INITIAL_STOCK, product.getId(), product.getStockQuantity(), actor);
    }

    /** Section 2.5 for seeding: sent in the background right after commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockCommandEntity initialStockAfterCommit(ProductEntity product, String actor) {
        return record(StockCommandEntity.Command.INITIAL_STOCK, product.getId(), product.getStockQuantity(), actor);
    }

    /**
     * Section 2.6: delete the stock item of a deleted product. An INITIAL_STOCK of the product that
     * was never sent is superseded (completed without a call); one already claimed is completed by
     * the dispatcher before the DELETE goes out.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockCommandEntity delete(long productId) {
        repository.completeUnsent(productId, StockCommandEntity.Command.INITIAL_STOCK, StockCommandEntity.Status.PENDING,
                StockCommandEntity.Status.DONE, commerceClock.instant(), SUPERSEDED);
        return record(StockCommandEntity.Command.DELETE, productId, null, null);
    }

    private StockCommandEntity record(StockCommandEntity.Command command, long productId, Integer quantity,
                                      String actor) {
        StockCommandEntity saved = save(command, productId, quantity, actor);
        dispatchAfterCommit();
        return saved;
    }

    private StockCommandEntity save(StockCommandEntity.Command command, long productId, Integer quantity,
                                    String actor) {
        return repository.save(StockCommandEntity.builder()
                .command(command)
                .productId(productId)
                .initialQuantity(quantity)
                .actor(actor)
                .requestId(UUID.randomUUID())
                .status(StockCommandEntity.Status.PENDING)
                .nextAttemptAt(commerceClock.instant())
                .correlationRequestId(CorrelationIds.currentRequestId())
                .traceparent(tracing.currentTraceparent())
                .createdAt(commerceClock.instant())
                .build());
    }

    private void dispatchAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(DISPATCH_REGISTERED)) {
            return;
        }
        TransactionSynchronizationManager.bindResource(DISPATCH_REGISTERED, Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatcher.dispatchSoon();
            }

            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(DISPATCH_REGISTERED);
            }
        });
    }
}
