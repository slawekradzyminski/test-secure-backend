package com.awesome.testing.service.stock;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Gives back a reservation whose reserve call had an unknown outcome (read timeout, connection
 * error or 5xx: the request may have reached warehouse-service and deducted the stock). Without
 * this, a timed-out checkout held the stock until warehouse-service's reaper released it, about
 * 2.5 minutes later, and every retry reserved again with a new id (exploratory chaos session,
 * finding F2).
 *
 * <p>The release runs on one background thread, so the 503 is not delayed by a second call over
 * the same slow link; a request sent later on that link reaches warehouse-service after the
 * reserve. A release that overtakes its reserve leaves a RELEASED tombstone there (contract 2.3),
 * so the late reserve deducts nothing. Best effort: a failed or dropped release is logged, and
 * the reaper remains the backstop.
 */
@Slf4j
@Component
public class AbandonedReservations implements DisposableBean {

    private static final int QUEUE_CAPACITY = 100;

    private final StockLedgerClient stockLedger;
    private final Executor executor;

    public AbandonedReservations(StockLedgerClient stockLedger,
                                 @Value("${warehouse.abandoned-reservations.async:true}") boolean async) {
        this.stockLedger = stockLedger;
        this.executor = async ? new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
                    Thread thread = new Thread(runnable, "abandoned-reservation-release");
                    thread.setDaemon(true);
                    return thread;
                }, (runnable, pool) -> log.warn("Release queue full; warehouse-service's reaper will release it"))
                : Runnable::run;
    }

    /** Releases the reservation in the background (immediately when async is off, as in tests). */
    public void release(UUID reservationId) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        executor.execute(() -> releaseWith(mdc, reservationId));
    }

    private void releaseWith(Map<String, String> mdc, UUID reservationId) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        if (mdc != null) {
            MDC.setContextMap(mdc);
        }
        try {
            stockLedger.release(reservationId);
            log.info("Released stock reservation {} after its reserve call failed", reservationId);
        } catch (RuntimeException e) {
            log.warn("Could not release stock reservation {} after its reserve call failed; "
                    + "warehouse-service's reaper will: {}", reservationId, e.getMessage());
        } finally {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }

    @Override
    // The pool is this bean's own executor: shut down here, not closed by a try-with-resources.
    @SuppressWarnings("PMD.CloseResource")
    public void destroy() {
        if (executor instanceof ExecutorService service) {
            service.shutdown();
        }
    }
}
