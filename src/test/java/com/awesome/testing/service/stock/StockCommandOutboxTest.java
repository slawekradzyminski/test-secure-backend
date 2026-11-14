package com.awesome.testing.service.stock;

import com.awesome.testing.correlation.CorrelationIds;
import com.awesome.testing.correlation.CorrelationTracing;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.stock.StockCommandEntity;
import com.awesome.testing.repository.stock.StockCommandRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
class StockCommandOutboxTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    @Mock private StockCommandRepository repository;
    @Mock private StockCommandDispatcher dispatcher;

    private StockCommandOutbox outbox;

    @BeforeEach
    void setUp() {
        outbox = new StockCommandOutbox(repository, dispatcher, CorrelationTracing.disabled(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        List.copyOf(TransactionSynchronizationManager.getResourceMap().keySet())
                .forEach(TransactionSynchronizationManager::unbindResourceIfPossible);
    }

    @Test
    void initialStockRecordsAPendingRowDueNowWithTheRequestsCorrelation() {
        ProductEntity product = ProductEntity.builder().id(5L).stockQuantity(10).build();
        CorrelationIds ids = CorrelationIds.fromHeaders("req-5", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        StockCommandEntity row;
        try (CorrelationIds.Scope ignored = ids.open()) {
            row = outbox.initialStock(product, "admin");
        }

        assertThat(row.getCommand()).isEqualTo(StockCommandEntity.Command.INITIAL_STOCK);
        assertThat(row.getProductId()).isEqualTo(5L);
        assertThat(row.getInitialQuantity()).isEqualTo(10);
        assertThat(row.getActor()).isEqualTo("admin");
        assertThat(row.getRequestId()).isNotNull();
        assertThat(row.getStatus()).isEqualTo(StockCommandEntity.Status.PENDING);
        assertThat(row.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(row.getCreatedAt()).isEqualTo(NOW);
        assertThat(row.getCorrelationRequestId()).isEqualTo("req-5");
        assertThat(row.getTraceparent()).startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-");
    }

    @Test
    void deleteRecordsAPendingRowWithoutQuantityOrActor() {
        StockCommandEntity row = outbox.delete(6L);

        assertThat(row.getCommand()).isEqualTo(StockCommandEntity.Command.DELETE);
        assertThat(row.getProductId()).isEqualTo(6L);
        assertThat(row.getInitialQuantity()).isNull();
        assertThat(row.getActor()).isNull();
    }

    @Test
    void oneTransactionAsksForOneDispatchAfterCommitAndNoneOnRollback() {
        outbox.delete(1L);
        outbox.delete(2L);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        synchronizations.getFirst().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(dispatcher, never()).dispatchSoon();
        synchronizations.getFirst().afterCommit();
        verify(dispatcher).dispatchSoon();
        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
    }

    @Test
    void withoutSynchronizationTheRowWaitsForTheScheduledRun() {
        TransactionSynchronizationManager.clearSynchronization();

        outbox.delete(3L);

        verify(repository).save(any());
        verify(dispatcher, never()).dispatchSoon();
    }
}
