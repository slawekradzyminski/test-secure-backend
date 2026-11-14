package com.awesome.testing.dto.stock;

/** A reserved line with the ledger quantity after the deduction and the product's ledger version. */
public record ReservedLineDto(Long productId, Integer quantityAfter, Long ledgerVersion) {
}
