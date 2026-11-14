package com.awesome.testing.stock;

import com.awesome.testing.correlation.CorrelationTracing;
import com.awesome.testing.service.stock.StockLedgerClient;
import com.awesome.testing.service.stock.StockProjection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * The stock ledger client and projection for test slices ({@code @DataJpaTest}) that call
 * checkout or adjustments; the client talks to {@link FakeWarehouse}.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({StockLedgerClient.class, StockProjection.class})
public class StockSliceConfig {

    @Bean
    CorrelationTracing correlationTracing() {
        return CorrelationTracing.disabled();
    }
}
