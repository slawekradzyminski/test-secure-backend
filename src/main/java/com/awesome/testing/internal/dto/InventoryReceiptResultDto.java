package com.awesome.testing.internal.dto;

import java.util.List;

/** Movement ids in the order of the request lines; a replay returns the original ids. */
public record InventoryReceiptResultDto(List<Long> movementIds) {
}
