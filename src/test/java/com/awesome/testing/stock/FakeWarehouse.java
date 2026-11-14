package com.awesome.testing.stock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * One WireMock server per test JVM that plays warehouse-service's stock ledger API through
 * {@link FakeStockLedger}. {@link FakeWarehouseInitializer} points {@code warehouse.url} of every
 * Spring test context at it.
 */
public final class FakeWarehouse {

    private static final FakeStockLedger LEDGER = new FakeStockLedger();
    private static WireMockServer server;

    private FakeWarehouse() {
    }

    public static synchronized String baseUrl() {
        if (server == null) {
            server = new WireMockServer(options().dynamicPort().extensions(LEDGER).containerThreads(40));
            server.start();
            server.stubFor(WireMock.any(WireMock.urlMatching("/.*")).willReturn(WireMock.ok()));
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "fake-warehouse-stop"));
        }
        return server.baseUrl();
    }

    public static FakeStockLedger ledger() {
        return LEDGER;
    }

    /** Requests the fake received that match {@code pattern}. */
    public static List<LoggedRequest> requests(RequestPatternBuilder pattern) {
        baseUrl();
        return server.findAll(pattern);
    }
}
