package com.awesome.testing.stock;

import com.awesome.testing.correlation.CorrelationTracing;
import com.awesome.testing.service.stock.StockCommandDispatcher;
import com.awesome.testing.service.stock.StockLedgerClient;
import com.awesome.testing.service.stock.StockProjection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * The stock ledger client, projection and command dispatcher for test slices ({@code @DataJpaTest}) that call
 * checkout or adjustments; the client talks to {@link FakeWarehouse}.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({StockLedgerClient.class, StockProjection.class, StockCommandDispatcher.class})
public class StockSliceConfig {

    @Bean
    CorrelationTracing correlationTracing() {
        return CorrelationTracing.disabled();
    }

    /** The checkout safety net's dispatcher needs one; slices have no scheduler of their own. */
    @Bean
    org.springframework.scheduling.TaskScheduler stockSliceTaskScheduler() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler scheduler =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        return scheduler;
    }
}
