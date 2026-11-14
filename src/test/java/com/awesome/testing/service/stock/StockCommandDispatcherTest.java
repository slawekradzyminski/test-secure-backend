package com.awesome.testing.service.stock;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.correlation.CorrelationTracing;
import com.awesome.testing.dto.stock.CreateStockItemRequestDto;
import com.awesome.testing.dto.stock.StockLevelDto;
import com.awesome.testing.entity.stock.StockCommandEntity;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.stock.StockCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockCommandDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000007");

    @Mock private StockCommandRepository repository;
    @Mock private ProductRepository productRepository;
    @Mock private StockLedgerClient client;
    @Mock private StockProjection projection;
    @Mock private TaskScheduler taskScheduler;
    @Mock private PlatformTransactionManager transactionManager;

    private StockCommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = dispatcher(true);
    }

    private StockCommandDispatcher dispatcher(boolean enabled) {
        return new StockCommandDispatcher(repository, productRepository, client, projection,
                CorrelationTracing.disabled(), taskScheduler, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(5), enabled);
    }

    private StockCommandEntity row(long id, StockCommandEntity.Command command, long productId) {
        StockCommandEntity row = StockCommandEntity.builder().id(id).command(command).productId(productId)
                .initialQuantity(command == StockCommandEntity.Command.INITIAL_STOCK ? 10 : null)
                .actor(command == StockCommandEntity.Command.INITIAL_STOCK ? "admin" : null)
                .requestId(REQUEST).status(StockCommandEntity.Status.PENDING).attempts(0)
                .nextAttemptAt(NOW).createdAt(NOW).correlationRequestId("req-1")
                .traceparent("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01").build();
        lenient().when(repository.findById(id)).thenReturn(Optional.of(row));
        return row;
    }

    @Test
    void anInitialStockRowCreatesTheItemAppliesItsLevelAndIsDone() {
        StockCommandEntity row = row(1L, StockCommandEntity.Command.INITIAL_STOCK, 5L);
        when(repository.findDueForUpdate(StockCommandEntity.Status.PENDING, NOW,
                PageRequest.of(0, StockCommandDispatcher.BATCH_SIZE))).thenReturn(List.of(row));
        when(productRepository.existsById(5L)).thenReturn(true);
        when(client.createItem(5L, new CreateStockItemRequestDto(10, "admin", REQUEST)))
                .thenReturn(new StockLevelDto(5L, 10, 1L));

        assertThat(dispatcher.dispatchDue()).isOne();

        verify(projection).apply(5L, 10, 1L);
        assertThat(row.getStatus()).isEqualTo(StockCommandEntity.Status.DONE);
        assertThat(row.getAttempts()).isOne();
        assertThat(row.getProcessedAt()).isEqualTo(NOW);
        assertThat(row.getLastError()).isNull();
    }

    @Test
    void aRetriedRowThatSucceedsForgetsItsLastError() {
        StockCommandEntity row = row(8L, StockCommandEntity.Command.DELETE, 12L);
        row.setAttempts(3);
        row.setLastError("Inventory service unavailable");
        when(repository.findDueForUpdate(any(), any(), any())).thenReturn(List.of(row));

        dispatcher.dispatchDue();

        assertThat(row.getStatus()).isEqualTo(StockCommandEntity.Status.DONE);
        assertThat(row.getAttempts()).isEqualTo(4);
        assertThat(row.getLastError()).isNull();
    }

    @Test
    void aDeleteRowDeletesTheItem() {
        StockCommandEntity row = row(2L, StockCommandEntity.Command.DELETE, 6L);
        when(repository.findDueForUpdate(any(), any(), any())).thenReturn(List.of(row));

        assertThat(dispatcher.dispatchDue()).isOne();

        verify(client).deleteItem(6L);
        verifyNoInteractions(projection, productRepository);
        assertThat(row.getStatus()).isEqualTo(StockCommandEntity.Status.DONE);
    }

    @Test
    void theInitialStockOfADeletedProductCompletesWithoutACall() {
        StockCommandEntity row = row(3L, StockCommandEntity.Command.INITIAL_STOCK, 7L);
        when(repository.findDueForUpdate(any(), any(), any())).thenReturn(List.of(row));
        when(productRepository.existsById(7L)).thenReturn(false);

        assertThat(dispatcher.dispatchDue()).isOne();

        verifyNoInteractions(client, projection);
        assertThat(row.getStatus()).isEqualTo(StockCommandEntity.Status.DONE);
    }

    @Test
    void aFailedRowStaysPendingUntilTheRetryIntervalAndOtherRowsStillGo() {
        StockCommandEntity failing = row(4L, StockCommandEntity.Command.DELETE, 8L);
        StockCommandEntity next = row(5L, StockCommandEntity.Command.DELETE, 9L);
        when(repository.findDueForUpdate(any(), any(), any())).thenReturn(List.of(failing, next));
        doThrow(new CustomException(StockLedgerClient.UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE))
                .when(client).deleteItem(8L);

        assertThat(dispatcher.dispatchDue()).isOne();

        assertThat(failing.getStatus()).isEqualTo(StockCommandEntity.Status.PENDING);
        assertThat(failing.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(failing.getLastError()).isEqualTo(StockLedgerClient.UNAVAILABLE);
        assertThat(failing.getAttempts()).isOne();
        assertThat(next.getStatus()).isEqualTo(StockCommandEntity.Status.DONE);
    }

    @Test
    void aClaimedRowIsLeasedSoAnotherDispatcherSkipsIt() {
        StockCommandEntity row = row(6L, StockCommandEntity.Command.DELETE, 10L);
        when(repository.findDueForUpdate(any(), any(), any())).thenReturn(List.of(row));
        doThrow(new IllegalStateException("x".repeat(600))).when(client).deleteItem(10L);
        when(repository.findById(6L)).thenAnswer(invocation -> {
            assertThat(row.getNextAttemptAt()).isEqualTo(NOW.plus(StockCommandDispatcher.CLAIM_LEASE));
            return Optional.of(row);
        });

        dispatcher.dispatchDue();

        assertThat(row.getLastError()).hasSize(500);
    }

    @Test
    void anErrorWithoutAMessageIsRecordedByItsType() {
        StockCommandEntity row = row(7L, StockCommandEntity.Command.DELETE, 11L);
        when(repository.findDueForUpdate(any(), any(), any())).thenReturn(List.of(row));
        doThrow(new IllegalStateException()).when(client).deleteItem(11L);

        dispatcher.dispatchDue();

        assertThat(row.getLastError()).isEqualTo("IllegalStateException");
    }

    @Test
    void scheduledAndAfterCommitDispatchRunOnlyWhenEnabled() {
        when(repository.findDueForUpdate(any(), any(), any())).thenReturn(List.of());

        dispatcher.scheduledDispatch();
        dispatcher.dispatchSoon();
        verify(taskScheduler).schedule(any(Runnable.class), eq(NOW));
        StockCommandDispatcher disabled = dispatcher(false);
        disabled.scheduledDispatch();
        disabled.dispatchSoon();

        verify(repository).findDueForUpdate(any(), any(), any());
        verify(taskScheduler).schedule(any(Runnable.class), eq(NOW));
    }

    @Test
    void aFailingClaimIsLoggedNotThrownByTheScheduledRun() {
        when(repository.findDueForUpdate(any(), any(), any())).thenThrow(new IllegalStateException("db down"));

        dispatcher.scheduledDispatch();

        verify(client, never()).deleteItem(anyLong());
    }
}
