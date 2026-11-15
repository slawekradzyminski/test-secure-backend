package com.awesome.testing.service.stock;

import java.io.Serial;

/**
 * warehouse-service refused a stock command with 404 or 409. The facade maps it to the public error
 * the caller expects; anything else (5xx, timeouts, connection errors, unexpected 4xx) is a
 * {@code 503 Inventory service unavailable} instead.
 *
 * @param status    404 or 409
 * @param message   warehouse-service's {@code message}, or null when the body had none
 * @param productId warehouse-service's {@code productId}, or null when the body had none
 */
public class StockLedgerRejection extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int httpStatus;
    private final Long rejectedProductId;

    public StockLedgerRejection(int status, String message, Long productId) {
        super(message);
        this.httpStatus = status;
        this.rejectedProductId = productId;
    }

    public int status() {
        return httpStatus;
    }

    public Long productId() {
        return rejectedProductId;
    }

    public boolean isNotFound() {
        return httpStatus == 404;
    }

    public boolean isConflict() {
        return httpStatus == 409;
    }
}
