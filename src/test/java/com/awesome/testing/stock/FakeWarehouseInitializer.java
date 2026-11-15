package com.awesome.testing.stock;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registered in {@code META-INF/spring.factories} for every test context: gives the context its own
 * {@link FakeStockLedger} (bean {@code fakeStockLedger}) and bearer token, provides
 * {@code fake-warehouse.url} (application-test.yml uses it as {@code warehouse.url}) and, once the
 * context is refreshed, lets the ledger read the stock projection from this context's DataSource.
 */
public class FakeWarehouseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        FakeStockLedger ledger = FakeWarehouse.newLedger();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fake-warehouse-token",
                Map.of("warehouse.backend-service-token", ledger.token())));
        context.getEnvironment().getPropertySources().addLast(
                new MapPropertySource("fake-warehouse", Map.of("fake-warehouse.url", FakeWarehouse.baseUrl())));
        context.getBeanFactory().registerSingleton("fakeStockLedger", ledger);
        context.addApplicationListener(event -> {
            if (event instanceof ContextRefreshedEvent refreshed && refreshed.getApplicationContext() == context) {
                bind(context, ledger);
            }
        });
    }

    private static long micros(java.sql.Timestamp timestamp) {
        return timestamp == null ? 0 : timestamp.getTime() * 1000 + (timestamp.getNanos() / 1000) % 1000;
    }

    private static void bind(ConfigurableApplicationContext context, FakeStockLedger ledger) {
        if (context.getBeanNamesForType(DataSource.class).length == 0) {
            return;
        }
        JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        ledger.bindProjection(productId -> {
            try {
                List<long[]> rows = jdbc.query(
                        "select stock_quantity, stock_ledger_version, created_at from products where id = ?",
                        (row, index) -> new long[] {row.getLong(1), row.getLong(2), micros(row.getTimestamp(3))},
                        productId);
                return rows.stream().findFirst();
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        });
    }
}
