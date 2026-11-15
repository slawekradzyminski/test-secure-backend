package com.awesome.testing.service;

import com.awesome.testing.dto.stock.ReservationDto;
import com.awesome.testing.dto.stock.ReservedLineDto;
import com.awesome.testing.dto.stock.StockLineDto;
import com.awesome.testing.service.stock.StockLedgerClient;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/** Mockito stubs for the warehouse-service calls of checkout in OrderService unit tests. */
final class CheckoutStubs {

    private CheckoutStubs() {
    }

    /** Every reservation succeeds; each line reports {@code quantityAfter} 0 at ledger version 1. */
    static void reserveEverything(StockLedgerClient stockLedger) {
        lenient().when(stockLedger.reserve(any(), any(), any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            List<StockLineDto> lines = invocation.getArgument(2);
            return new ReservationDto(id, lines.stream()
                    .map(line -> new ReservedLineDto(line.productId(), 0, 1L)).toList());
        });
    }
}
