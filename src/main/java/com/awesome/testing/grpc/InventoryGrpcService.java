package com.awesome.testing.grpc;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.inventory.InventoryAdjustmentDto;
import com.awesome.testing.dto.inventory.InventoryItemDto;
import com.awesome.testing.dto.inventory.InventoryMovementDto;
import com.awesome.testing.dto.inventory.StockStatus;
import com.awesome.testing.grpc.proto.AdjustStockRequest;
import com.awesome.testing.grpc.proto.GetStockRequest;
import com.awesome.testing.grpc.proto.InventoryItem;
import com.awesome.testing.grpc.proto.InventoryPage;
import com.awesome.testing.grpc.proto.InventoryServiceGrpc;
import com.awesome.testing.grpc.proto.ListInventoryRequest;
import com.awesome.testing.grpc.proto.ListStockMovementsRequest;
import com.awesome.testing.grpc.proto.MovementPage;
import com.awesome.testing.grpc.proto.StockMovement;
import com.awesome.testing.service.InventoryService;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.grpc.server.service.GrpcService;

import java.util.UUID;
import java.util.function.Supplier;

@GrpcService
@Profile("grpc")
@RequiredArgsConstructor
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {
    private final InventoryService inventory;
    private final Validator validator;

    @Override
    public void getStock(GetStockRequest request, StreamObserver<InventoryItem> response) {
        respond(response, () -> InventoryGrpcViews.item(inventory.get(productId(request.getProductId()),
                threshold(request.hasLowStockThreshold(), request.getLowStockThreshold()))));
    }

    @Override
    public void listInventory(ListInventoryRequest request, StreamObserver<InventoryPage> response) {
        respond(response, () -> {
            PageRequest pagination = pagination(request.getPage(), request.hasSize(), request.getSize());
            StockStatus status = request.hasStatus() ? stockStatus(request.getStatus()) : null;
            Page<InventoryItemDto> page = inventory.list(pagination.getPageNumber(), pagination.getPageSize(),
                    request.hasSearch() ? request.getSearch() : null,
                    request.hasCategory() ? request.getCategory() : null, status,
                    threshold(request.hasLowStockThreshold(), request.getLowStockThreshold()));
            return InventoryPage.newBuilder().addAllItems(page.map(InventoryGrpcViews::item))
                    .setTotal(page.getTotalElements()).setPage(page.getNumber()).setSize(page.getSize()).build();
        });
    }

    @Override
    public void adjustStock(AdjustStockRequest request, StreamObserver<StockMovement> response) {
        respond(response, () -> {
            UUID requestId = UUID.fromString(request.getRequestId());
            if (!requestId.toString().equalsIgnoreCase(request.getRequestId())) {
                throw new IllegalArgumentException("Canonical request UUID required");
            }
            InventoryAdjustmentDto adjustment = new InventoryAdjustmentDto(request.getDelta(), request.getReason(), requestId);
            if (!validator.validate(adjustment).isEmpty()) {
                throw new IllegalArgumentException("Invalid adjustment");
            }
            return InventoryGrpcViews.movement(inventory.adjust(productId(request.getProductId()), adjustment,
                    InventoryGrpcAuthentication.actor()));
        });
    }

    @Override
    public void listStockMovements(ListStockMovementsRequest request, StreamObserver<MovementPage> response) {
        respond(response, () -> {
            Page<InventoryMovementDto> page = inventory.movements(productId(request.getProductId()),
                    pagination(request.getPage(), request.hasSize(), request.getSize()));
            return MovementPage.newBuilder().addAllItems(page.map(InventoryGrpcViews::movement))
                    .setTotal(page.getTotalElements()).setPage(page.getNumber()).setSize(page.getSize()).build();
        });
    }

    private static long productId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Positive product ID required");
        }
        return id;
    }

    private static int threshold(boolean supplied, int value) {
        return supplied ? value : 10;
    }

    private static PageRequest pagination(int page, boolean hasSize, int size) {
        int effectiveSize = hasSize ? size : 20;
        if (page < 0 || effectiveSize < 1 || effectiveSize > 100) {
            throw new IllegalArgumentException("Invalid pagination");
        }
        return PageRequest.of(page, effectiveSize);
    }

    private static StockStatus stockStatus(com.awesome.testing.grpc.proto.StockStatus status) {
        return switch (status) {
            case IN_STOCK -> StockStatus.IN_STOCK;
            case LOW_STOCK -> StockStatus.LOW_STOCK;
            case OUT_OF_STOCK -> StockStatus.OUT_OF_STOCK;
            default -> throw new IllegalArgumentException("Invalid stock status");
        };
    }

    private static <T> void respond(StreamObserver<T> response, Supplier<T> operation) {
        try {
            InventoryGrpcAuthentication.actor();
            if (Context.current().isCancelled()) {
                throw Status.CANCELLED.withDescription("Call cancelled").asRuntimeException();
            }
            T result = operation.get();
            response.onNext(result);
            response.onCompleted();
        } catch (RuntimeException exception) {
            response.onError(status(exception).asRuntimeException());
        }
    }

    private static Status status(RuntimeException exception) {
        return switch (exception) {
            case StatusRuntimeException grpc -> grpc.getStatus();
            case IllegalArgumentException ignored -> Status.INVALID_ARGUMENT.withDescription("Invalid inventory request");
            case ConstraintViolationException ignored -> Status.INVALID_ARGUMENT.withDescription("Invalid inventory request");
            case CustomException domain -> switch (domain.getHttpStatus().value()) {
                case 400 -> Status.INVALID_ARGUMENT.withDescription("Invalid inventory request");
                case 404 -> Status.NOT_FOUND.withDescription("Product not found");
                case 409 -> Status.FAILED_PRECONDITION.withDescription("Inventory adjustment conflicts with current state");
                default -> Status.INTERNAL.withDescription("Inventory operation failed");
            };
            default -> Status.INTERNAL.withDescription("Inventory operation failed");
        };
    }
}
