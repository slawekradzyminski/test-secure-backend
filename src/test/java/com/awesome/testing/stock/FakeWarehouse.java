package com.awesome.testing.stock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * One WireMock server per test JVM that plays warehouse-service's stock ledger API. Every Spring
 * test context gets its own {@link FakeStockLedger} and its own {@code BACKEND_SERVICE_TOKEN}
 * ({@link FakeWarehouseInitializer}); requests are routed to the ledger by that bearer token.
 * Contexts use different H2 databases whose product ids overlap, so a shared ledger would mix
 * their products up.
 */
public final class FakeWarehouse {

    private static final Map<String, FakeStockLedger> LEDGERS = new ConcurrentHashMap<>();
    private static final AtomicInteger CONTEXTS = new AtomicInteger();
    private static WireMockServer server;

    private FakeWarehouse() {
    }

    public static synchronized String baseUrl() {
        if (server == null) {
            server = new WireMockServer(options().dynamicPort().extensions(new Router()).containerThreads(40));
            server.start();
            server.stubFor(WireMock.any(WireMock.urlMatching("/.*")).willReturn(WireMock.ok()));
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "fake-warehouse-stop"));
        }
        return server.baseUrl();
    }

    /** A new, empty ledger for one Spring test context, with its own bearer token. */
    static FakeStockLedger newLedger() {
        FakeStockLedger ledger = new FakeStockLedger("test-backend-token-" + CONTEXTS.incrementAndGet());
        LEDGERS.put("Bearer " + ledger.token(), ledger);
        return ledger;
    }

    /** Requests the server received (from every context) that match {@code pattern}. */
    static List<LoggedRequest> requests(RequestPatternBuilder pattern) {
        baseUrl();
        return server.findAll(pattern);
    }

    private static final class Router implements ResponseDefinitionTransformerV2 {

        @Override
        public String getName() {
            return "fake-warehouse-router";
        }

        @Override
        public ResponseDefinition transform(ServeEvent serveEvent) {
            String authorization = serveEvent.getRequest().getHeader("Authorization");
            FakeStockLedger ledger = authorization == null ? null : LEDGERS.get(authorization);
            if (ledger == null) {
                return responseDefinition().withStatus(401).withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Invalid service token\"}").build();
            }
            return ledger.handle(serveEvent.getRequest());
        }
    }
}
