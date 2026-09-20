package com.awesome.testing.grpc;

import com.awesome.testing.dto.inventory.InventoryItemDto;
import com.awesome.testing.dto.inventory.InventoryMovementDto;
import com.awesome.testing.grpc.proto.InventoryItem;
import com.awesome.testing.grpc.proto.StockMovement;
import com.awesome.testing.grpc.proto.StockStatus;

final class InventoryGrpcViews {
    private InventoryGrpcViews() { }

    static InventoryItem item(InventoryItemDto item) {
        InventoryItem.Builder result = InventoryItem.newBuilder()
                .setProductId(item.getProductId()).setName(item.getName())
                .setAvailableQuantity(item.getAvailableQuantity())
                .setStockStatus(StockStatus.valueOf(item.getStockStatus().name()));
        if (item.getCategory() != null) {
            result.setCategory(item.getCategory());
        }
        if (item.getLastChangedAt() != null) {
            result.setLastChangedAt(item.getLastChangedAt().toString());
        }
        return result.build();
    }

    static StockMovement movement(InventoryMovementDto movement) {
        StockMovement.Builder result = StockMovement.newBuilder()
                .setId(movement.getId()).setProductId(movement.getProductId())
                .setType(movement.getType().name()).setDelta(movement.getDelta())
                .setQuantityAfter(movement.getQuantityAfter()).setCreatedAt(movement.getCreatedAt().toString());
        if (movement.getOrderId() != null) {
            result.setOrderId(movement.getOrderId());
        }
        if (movement.getActor() != null) {
            result.setActor(movement.getActor());
        }
        if (movement.getReason() != null) {
            result.setReason(movement.getReason());
        }
        if (movement.getRequestId() != null) {
            result.setRequestId(movement.getRequestId().toString());
        }
        return result.build();
    }
}
