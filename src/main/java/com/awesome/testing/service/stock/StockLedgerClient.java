package com.awesome.testing.service.stock;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.correlation.CorrelationIds;
import com.awesome.testing.correlation.CorrelationTracing;
import com.awesome.testing.dto.inventory.InventoryMovementDto;
import com.awesome.testing.dto.stock.AdjustStockRequestDto;
import com.awesome.testing.dto.stock.ConfirmReservationRequestDto;
import com.awesome.testing.dto.stock.CreateStockItemRequestDto;
import com.awesome.testing.dto.stock.LedgerMovementDto;
import com.awesome.testing.dto.stock.ReservationDto;
import com.awesome.testing.dto.stock.ReserveStockRequestDto;
import com.awesome.testing.dto.stock.StockLevelDto;
import com.awesome.testing.dto.stock.StockLineDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HTTP client of warehouse-service's stock ledger ({@code /internal/v1/stock}, contract
 * INVENTORY_OWNERSHIP_CONTRACT.md section 2). Every call sends {@code Authorization: Bearer
 * <BACKEND_SERVICE_TOKEN>}, {@code X-Request-Id} and {@code traceparent}, and is bounded by the
 * connect timeout (2 s) and the read timeout (5 s).
 *
 * <ul>
 *   <li>2xx: the parsed body.</li>
 *   <li>404 and 409: {@link StockLedgerRejection}, which the caller maps to its public error.</li>
 *   <li>5xx, any other status, a timeout, a connection error or an unreadable body:
 *       {@code 503 Inventory service unavailable}.</li>
 * </ul>
 *
 * <p>The connection pool is small (3 in production), so a call made while the calling thread holds
 * a database transaction would keep a connection busy for up to the read timeout. Every method
 * therefore refuses to run inside a transaction ({@link IllegalStateException}).
 */
@Slf4j
@Component
public class StockLedgerClient {

    public static final String UNAVAILABLE = "Inventory service unavailable";
    static final String BASE_PATH = "/internal/v1/stock";
    private static final int NOT_FOUND = 404;
    private static final int CONFLICT = 409;
    private static final int SERVER_ERROR = 500;

    private final RestClient restClient;
    private final String bearerToken;
    private final CorrelationTracing tracing;
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public StockLedgerClient(
            @Value("${warehouse.url:http://warehouse-service:4003}") String baseUrl,
            @Value("${warehouse.connect-timeout:PT2S}") Duration connectTimeout,
            @Value("${warehouse.read-timeout:PT5S}") Duration readTimeout,
            @Value("${warehouse.backend-service-token:}") String bearerToken,
            CorrelationTracing tracing) {
        // Lives as long as this singleton bean; the JDK client releases its resources when unreachable.
        @SuppressWarnings("PMD.CloseResource")
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.bearerToken = bearerToken == null ? "" : bearerToken.trim();
        this.tracing = tracing;
        if (this.bearerToken.isEmpty()) {
            log.warn("BACKEND_SERVICE_TOKEN is not set: warehouse-service will reject stock commands "
                    + "and they will answer 503 {}", UNAVAILABLE);
        }
    }

    /** 2.1: deducts every line or nothing; 409 for the first short product, 404 for an unknown one. */
    public ReservationDto reserve(UUID reservationId, String username, List<StockLineDto> lines) {
        return call(HttpMethod.POST, BASE_PATH + "/reservations",
                new ReserveStockRequestDto(reservationId, username, lines), ReservationDto.class);
    }

    /** 2.2: ties the reservation to the order; idempotent. */
    public ReservationDto confirm(UUID reservationId, long orderId) {
        return call(HttpMethod.POST, BASE_PATH + "/reservations/" + reservationId + "/confirm",
                new ConfirmReservationRequestDto(orderId), ReservationDto.class);
    }

    /** 2.3: puts back the stock of an unconfirmed reservation; idempotent. */
    public void release(UUID reservationId) {
        call(HttpMethod.POST, BASE_PATH + "/reservations/" + reservationId + "/release", null, JsonNode.class);
    }

    /** 2.4: an admin adjustment, with the ledger version after it. */
    public LedgerMovementDto adjust(AdjustStockRequestDto request) {
        return call(HttpMethod.POST, BASE_PATH + "/adjustments", request, LedgerMovementDto.class);
    }

    /** 2.5: creates the stock item of a product (a no-op returning the current item when it exists). */
    public StockLevelDto createItem(long productId, CreateStockItemRequestDto request) {
        return call(HttpMethod.PUT, BASE_PATH + "/items/" + productId, request, StockLevelDto.class);
    }

    /** 2.6: deletes the stock item, its movements and reservations; idempotent. */
    public void deleteItem(long productId) {
        call(HttpMethod.DELETE, BASE_PATH + "/items/" + productId, null, JsonNode.class);
    }

    /** 2.7: a page of the product's movements, newest first. */
    public Page<InventoryMovementDto> movements(long productId, int page, int size) {
        JsonNode body = call(HttpMethod.GET,
                BASE_PATH + "/movements?productId=" + productId + "&page=" + page + "&size=" + size,
                null, JsonNode.class);
        List<InventoryMovementDto> content = new ArrayList<>();
        for (JsonNode movement : body.path("content")) {
            content.add(json.treeToValue(movement, LedgerMovementDto.class).toMovement());
        }
        // A Spring Page serializes either flat (totalElements) or as a PagedModel ({page: {...}}).
        JsonNode total = body.has("totalElements") ? body.get("totalElements") : body.path("page").path("totalElements");
        long totalElements = total.isNumber() ? total.asLong() : content.size();
        return new PageImpl<>(content, PageRequest.of(page, size), totalElements);
    }

    private <T> T call(HttpMethod method, String uri, Object body, Class<T> responseType) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "warehouse-service must not be called while a database transaction is open: " + method + " " + uri);
        }
        CorrelationIds ids = CorrelationIds.current().orElseGet(() ->
                CorrelationIds.fromHeaders(null, null));
        try {
            RestClient.RequestBodySpec request = restClient.method(method)
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (!bearerToken.isEmpty()) {
                            headers.setBearerAuth(bearerToken);
                        }
                        headers.set(CorrelationIds.REQUEST_ID_HEADER, ids.requestId());
                        headers.set(CorrelationIds.TRACEPARENT_HEADER, tracing.outgoingTraceparent(ids));
                    });
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body));
            }
            return request.exchange((clientRequest, response) ->
                    handle(method, uri, response.getStatusCode().value(), readBody(response.getBody()), responseType),
                    true);
        } catch (StockLedgerRejection | CustomException e) {
            throw e;
        } catch (RestClientException | JacksonException e) {
            log.warn("warehouse-service {} {} failed: {}", method, uri, e.toString());
            throw unavailableError(e);
        }
    }

    private <T> T handle(HttpMethod method, String uri, int status, String text, Class<T> responseType) {
        if (status >= 200 && status < 300) {
            return text.isBlank() && responseType == JsonNode.class
                    ? responseType.cast(json.createObjectNode())
                    : json.readValue(text, responseType);
        }
        if (status == NOT_FOUND || status == CONFLICT) {
            JsonNode error = parseQuietly(text);
            JsonNode productId = error.path("productId");
            throw new StockLedgerRejection(status,
                    error.path("message").isString() ? error.path("message").asString() : null,
                    productId.isNumber() ? productId.asLong() : null);
        }
        if (status >= SERVER_ERROR) {
            log.warn("warehouse-service {} {} answered {}: {}", method, uri, status, abbreviate(text));
        } else {
            log.error("warehouse-service {} {} answered unexpected {}: {}", method, uri, status, abbreviate(text));
        }
        throw unavailableError(null);
    }

    private JsonNode parseQuietly(String text) {
        try {
            return text.isBlank() ? json.createObjectNode() : json.readTree(text);
        } catch (JacksonException e) {
            return json.createObjectNode();
        }
    }

    private static String readBody(InputStream body) throws IOException {
        return body == null ? "" : new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String abbreviate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }

    private static CustomException unavailableError(Throwable cause) {
        return new CustomException(UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, cause);
    }
}
