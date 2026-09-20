package com.awesome.testing.grpc;

import com.awesome.testing.dto.inventory.InventoryAdjustmentDto;
import com.awesome.testing.grpc.proto.GetStockRequest;
import com.awesome.testing.grpc.proto.ListStockMovementsRequest;
import com.awesome.testing.service.InventoryService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

class InventoryGrpcFailureTest extends AbstractInventoryGrpcTest {
    @MockitoSpyBean
    private InventoryService failingInventory;

    @Test
    void unexpectedFailuresDoNotExposeInternalExceptionDetails() {
        // given
        var product = setupProduct();
        var client = stub(admin());
        doThrow(new IllegalStateException("private database connection details"))
                .when(failingInventory).get(product.getId(), 10);

        // when / then
        assertThatThrownBy(() -> client.getStock(GetStockRequest.newBuilder().setProductId(product.getId()).build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class, error -> {
                    assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                    assertThat(error.getStatus().getDescription()).isEqualTo("Inventory operation failed");
                });
    }

    @Test
    void retryingTheSameRequestAfterAnInFlightDeadlineDoesNotApplyTheWriteTwice() throws Exception {
        // given
        var product = setupProduct();
        var client = stub(admin());
        var request = adjustment(product.getId(), 3);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var completed = new CountDownLatch(1);
        var pauseFirstCall = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (pauseFirstCall.compareAndSet(true, false)) {
                started.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                try {
                    return invocation.callRealMethod();
                } finally {
                    completed.countDown();
                }
            }
            return invocation.callRealMethod();
        }).when(failingInventory).adjust(eq(product.getId()), any(InventoryAdjustmentDto.class), anyString());

        // when
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                try {
                    client.withDeadlineAfter(500, TimeUnit.MILLISECONDS).adjustStock(request);
                    return Status.Code.OK;
                } catch (StatusRuntimeException error) {
                    return error.getStatus().getCode();
                }
            });
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(Status.Code.DEADLINE_EXCEEDED);
            } finally {
                release.countDown();
            }
        }
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        var replay = client.adjustStock(request);

        // then
        assertThat(replay.getQuantityAfter()).isEqualTo(product.getStockQuantity() + 3);
        assertThat(client.getStock(GetStockRequest.newBuilder().setProductId(product.getId()).build()).getAvailableQuantity())
                .isEqualTo(product.getStockQuantity() + 3);
        assertThat(client.listStockMovements(ListStockMovementsRequest.newBuilder().setProductId(product.getId()).build())
                .getTotal()).isEqualTo(1);
    }

}
