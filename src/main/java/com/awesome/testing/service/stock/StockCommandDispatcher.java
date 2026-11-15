package com.awesome.testing.service.stock;

import com.awesome.testing.correlation.CorrelationIds;
import com.awesome.testing.correlation.CorrelationTracing;
import com.awesome.testing.dto.stock.CreateStockItemRequestDto;
import com.awesome.testing.dto.stock.StockLevelDto;
import com.awesome.testing.entity.stock.StockCommandEntity;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.stock.StockCommandRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends {@code stock_command_outbox} rows to warehouse-service: right after the transaction that
 * wrote them commits ({@link #dispatchSoon()}), and every {@code warehouse.stock-commands.retry-interval}
 * (default PT5S) for rows that failed or were lost in a restart.
 *
 * <p>Rows are claimed in a short transaction with {@code FOR UPDATE SKIP LOCKED}, which pushes their
 * next attempt one lease ahead so another instance does not send them at the same time; the HTTP
 * call runs with no transaction open; the outcome is written in another short transaction. Both
 * commands are idempotent on warehouse-service, so a row sent twice (a crash after the call) is safe.
 * An INITIAL_STOCK row whose product was deleted meanwhile is completed without a call.
 */
@Slf4j
@Component
public class StockCommandDispatcher {

    static final int BATCH_SIZE = 50;
    static final int MAX_ROUNDS = 10;
    static final Duration CLAIM_LEASE = Duration.ofMinutes(1);
    static final String SPAN = "send stock command";

    private final StockCommandRepository repository;
    private final ProductRepository productRepository;
    private final StockLedgerClient client;
    private final StockProjection projection;
    private final CorrelationTracing tracing;
    private final TaskScheduler taskScheduler;
    private final TransactionTemplate transactions;
    private final Clock commerceClock;
    private final Duration retryInterval;
    private final boolean enabled;

    public StockCommandDispatcher(StockCommandRepository repository,
                                  ProductRepository productRepository,
                                  StockLedgerClient client,
                                  StockProjection projection,
                                  CorrelationTracing tracing,
                                  TaskScheduler taskScheduler,
                                  PlatformTransactionManager transactionManager,
                                  Clock commerceClock,
                                  @Value("${warehouse.stock-commands.retry-interval:PT5S}") Duration retryInterval,
                                  @Value("${warehouse.stock-commands.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.client = client;
        this.projection = projection;
        this.tracing = tracing;
        this.taskScheduler = taskScheduler;
        this.transactions = new TransactionTemplate(transactionManager);
        this.commerceClock = commerceClock;
        this.retryInterval = retryInterval;
        this.enabled = enabled;
    }

    /** A claimed row, detached from the persistence context. */
    record Claimed(long id, StockCommandEntity.Command command, long productId, Integer initialQuantity,
                   String actor, UUID requestId, String correlationRequestId, String traceparent) {
    }

    @Scheduled(fixedDelayString = "${warehouse.stock-commands.retry-interval:PT5S}",
            initialDelayString = "${warehouse.stock-commands.initial-delay:PT5S}")
    public void scheduledDispatch() {
        if (enabled) {
            dispatchQuietly();
        }
    }

    /** Sends due rows on the scheduler thread pool now (after a commit that wrote rows). */
    public void dispatchSoon() {
        if (enabled) {
            taskScheduler.schedule(this::dispatchQuietly, commerceClock.instant());
        }
    }

    private void dispatchQuietly() {
        try {
            dispatchDue();
        } catch (RuntimeException e) {
            log.warn("Stock command dispatch failed; retrying on the next run", e);
        }
    }

    /**
     * Sends due rows in the calling thread, batch after batch, so a command that waited for an older
     * command of the same product goes out as soon as that one completes; returns how many rows were
     * completed. Failed rows move to their next attempt, so the rounds end.
     */
    public int dispatchDue() {
        int done = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<Claimed> claimed = Objects.requireNonNull(transactions.execute(status -> claim()));
            if (claimed.isEmpty()) {
                break;
            }
            for (Claimed command : claimed) {
                if (send(command)) {
                    done++;
                }
            }
        }
        return done;
    }

    /**
     * Sends one pending row now in the calling thread (contract section 4.3: the initial stock of a
     * new product goes out before the create response). The row is claimed like in a scheduled run;
     * a row another dispatcher holds, or one already done, is left alone. A failure keeps the row for
     * the scheduled retry and is not thrown. Must be called with no transaction open.
     *
     * @return true when warehouse-service accepted the command
     */
    public boolean sendNow(long commandId) {
        Optional<Claimed> claimed = Objects.requireNonNull(transactions.execute(status ->
                repository.findPendingForUpdate(commandId, StockCommandEntity.Status.PENDING)
                        .map(row -> lease(row, commerceClock.instant()))));
        return claimed.isPresent() && send(claimed.get());
    }

    /**
     * Safety net of checkout (contract section 4.3): sends the pending INITIAL_STOCK of a product
     * now, when warehouse-service does not know the product yet.
     *
     * @return true when such a command was pending and warehouse-service accepted it
     */
    public boolean sendPendingInitialStock(long productId) {
        return repository.findFirstByProductIdAndCommandAndStatusOrderByIdAsc(productId,
                        StockCommandEntity.Command.INITIAL_STOCK, StockCommandEntity.Status.PENDING)
                .map(row -> sendNow(row.getId()))
                .orElse(false);
    }

    private List<Claimed> claim() {
        Instant now = commerceClock.instant();
        return repository.findDueForUpdate(StockCommandEntity.Status.PENDING, now, PageRequest.of(0, BATCH_SIZE))
                .stream()
                .map(row -> lease(row, now))
                .toList();
    }

    private Claimed lease(StockCommandEntity row, Instant now) {
        row.setAttempts(row.getAttempts() + 1);
        row.setNextAttemptAt(now.plus(CLAIM_LEASE));
        return new Claimed(row.getId(), row.getCommand(), row.getProductId(), row.getInitialQuantity(),
                row.getActor(), row.getRequestId(), row.getCorrelationRequestId(), row.getTraceparent());
    }

    private boolean send(Claimed command) {
        try (CorrelationIds.Scope ignored = tracing.resume(SPAN, command.correlationRequestId(), command.traceparent())) {
            switch (command.command()) {
                case INITIAL_STOCK -> sendInitialStock(command);
                case DELETE -> client.deleteItem(command.productId());
            }
            complete(command.id());
            return true;
        } catch (RuntimeException e) {
            log.warn("Stock command {} {} for product {} failed (retrying in {}): {}", command.id(),
                    command.command(), command.productId(), retryInterval, e.getMessage());
            fail(command.id(), e);
            return false;
        }
    }

    private void sendInitialStock(Claimed command) {
        if (!productRepository.existsById(command.productId())) {
            log.info("Product {} was deleted before its initial stock reached warehouse-service", command.productId());
            return;
        }
        StockLevelDto item = client.createItem(command.productId(),
                new CreateStockItemRequestDto(command.initialQuantity(), command.actor(), command.requestId()));
        projection.apply(command.productId(), item.quantity(), item.ledgerVersion());
    }

    private void complete(long id) {
        transactions.executeWithoutResult(status -> repository.findById(id).ifPresent(row -> {
            row.setStatus(StockCommandEntity.Status.DONE);
            row.setProcessedAt(commerceClock.instant());
            row.setLastError(null);
        }));
    }

    private void fail(long id, RuntimeException error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        transactions.executeWithoutResult(status -> repository.findById(id).ifPresent(row -> {
            row.setNextAttemptAt(commerceClock.instant().plus(retryInterval));
            row.setLastError(message.length() <= 500 ? message : message.substring(0, 500));
        }));
    }
}
