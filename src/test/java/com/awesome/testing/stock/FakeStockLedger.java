package com.awesome.testing.stock;

import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;

/**
 * An in-memory stand-in for warehouse-service's stock ledger API ({@code /internal/v1/stock}),
 * served by WireMock (see {@link FakeWarehouse}) so the backend's real HTTP client is exercised in
 * every Spring test. It implements the contract's rules for reservations, confirmations, releases,
 * adjustments, stock items and movements.
 *
 * <p>Tests create products directly in the database, so a product the fake has not seen yet is
 * seeded from the backend's projection ({@code products.stock_quantity}, {@code stock_ledger_version})
 * through {@link ProjectionLookup}. A test that later overwrites the projection directly (same
 * ledger version, different quantity) is followed too; a projection that lags behind the fake
 * (lower version, a reply not applied yet) is not.
 *
 * <p>Faults are injected per product id ({@link #fail}), so concurrently running test classes,
 * which always use their own products, do not disturb each other.
 */
public class FakeStockLedger implements ResponseDefinitionTransformerV2 {

    public static final String TOKEN = "test-backend-token";

    /** Which stock commands a fault applies to. */
    public enum Operation { RESERVE, CONFIRM, RELEASE, ADJUST, ITEMS, MOVEMENTS }

    /** What a faulty command gets: a status, a delay beyond the client's read timeout or a reset. */
    public record Injected(int status, Duration delay, boolean reset, Set<Operation> operations) {

        public static Injected status(int status, Operation first, Operation... more) {
            return new Injected(status, null, false, EnumSet.of(first, more));
        }

        public static Injected timeout(Operation first, Operation... more) {
            return new Injected(200, Duration.ofMillis(2500), false, EnumSet.of(first, more));
        }

        public static Injected reset(Operation first, Operation... more) {
            return new Injected(0, null, true, EnumSet.of(first, more));
        }
    }

    /**
     * The backend's projection of a product: {quantity, ledger version, creation time in epoch
     * microseconds}. Test contexts recreate the H2 schema, so product ids are reused; the creation
     * time tells a new product from an old one with the same id.
     */
    @FunctionalInterface
    public interface ProjectionLookup {
        Optional<long[]> find(long productId);
    }

    public record Item(int quantity, long version, long createdAt) {
    }

    public record Movement(long id, long productId, Long orderId, String type, int delta, int quantityAfter,
                           String actor, String reason, UUID requestId, LocalDateTime createdAt, long version) {
    }

    private record Reservation(UUID id, Map<Long, Integer> lines, String username, String status,
                               List<Map<String, Object>> result, List<Long> movementIds) {
        Reservation withStatus(String newStatus) {
            return new Reservation(id, lines, username, newStatus, result, movementIds);
        }
    }

    private static final Pattern RESERVATION = Pattern.compile("/internal/v1/stock/reservations/([^/]+)/(confirm|release)");
    private static final Pattern ITEM = Pattern.compile("/internal/v1/stock/items/(\\d+)");

    private final JsonMapper json = JsonMapper.builder().build();
    private final Clock clock = Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    private final AtomicLong movementIds = new AtomicLong(1_000_000);
    private final Map<Long, Item> items = new HashMap<>();
    private final Map<UUID, Reservation> reservations = new HashMap<>();
    private final List<Movement> movements = new ArrayList<>();
    private final Map<Long, Injected> faults = new ConcurrentHashMap<>();
    private volatile ProjectionLookup projection = productId -> Optional.empty();

    @Override
    public String getName() {
        return "fake-stock-ledger";
    }

    public void bindProjection(ProjectionLookup lookup) {
        this.projection = lookup;
    }

    /** Commands of {@code fault.operations()} that touch {@code productId} get {@code fault}. */
    public void fail(long productId, Injected fault) {
        faults.put(productId, fault);
    }

    public void heal(long productId) {
        faults.remove(productId);
    }

    /** The ledger's item, seeded from the projection like a request would. */
    public synchronized Optional<Item> item(long productId) {
        return Optional.ofNullable(resolve(productId));
    }

    /** Sets the ledger quantity directly (a new version), like a receipt in warehouse-service would. */
    public synchronized Item set(long productId, int quantity) {
        Item current = resolve(productId);
        return put(productId, quantity, current == null ? 1 : current.version() + 1);
    }

    /** The product's movements, newest first (after re-synchronising with the projection, like a request). */
    public synchronized List<Movement> movements(long productId) {
        resolve(productId);
        return movements.stream().filter(movement -> movement.productId() == productId)
                .sorted(Comparator.comparing(Movement::id).reversed()).toList();
    }

    /**
     * What warehouse-service does with an {@code order.cancelled} event carrying {@code stockRestore}:
     * one ORDER_RESTORED movement per line with reason "Order cancellation". Returns the new levels
     * (product id to item), which the caller pushes to the backend.
     */
    public synchronized Map<Long, Item> restore(long orderId, String actor, Map<Long, Integer> lines) {
        Map<Long, Item> levels = new TreeMap<>();
        new TreeMap<>(lines).forEach((productId, quantity) -> {
            Item current = resolve(productId);
            int base = current == null ? 0 : current.quantity();
            levels.put(productId, write(productId, orderId, "ORDER_RESTORED", quantity, base, actor,
                    "Order cancellation", null));
        });
        return levels;
    }

    @Override
    public ResponseDefinition transform(ServeEvent serveEvent) {
        Request request = serveEvent.getRequest();
        if (!("Bearer " + TOKEN).equals(request.getHeader("Authorization"))) {
            return respond(401, Map.of("message", "Invalid service token"));
        }
        URI uri = URI.create(request.getUrl());
        String path = uri.getPath();
        String method = request.getMethod().getName();
        try {
            if (method.equals("POST") && path.equals("/internal/v1/stock/reservations")) {
                return reserve(json.readTree(request.getBodyAsString()));
            }
            Matcher reservation = RESERVATION.matcher(path);
            if (method.equals("POST") && reservation.matches()) {
                UUID id = UUID.fromString(reservation.group(1));
                return reservation.group(2).equals("confirm")
                        ? confirm(id, json.readTree(request.getBodyAsString()).path("orderId").asLong())
                        : release(id);
            }
            if (method.equals("POST") && path.equals("/internal/v1/stock/adjustments")) {
                return adjust(json.readTree(request.getBodyAsString()));
            }
            Matcher item = ITEM.matcher(path);
            if (item.matches()) {
                long productId = Long.parseLong(item.group(1));
                return switch (method) {
                    case "PUT" -> createItem(productId, json.readTree(request.getBodyAsString()));
                    case "DELETE" -> deleteItem(productId);
                    case "GET" -> getItem(productId);
                    default -> respond(405, Map.of("message", "Method not allowed"));
                };
            }
            if (method.equals("GET") && path.equals("/internal/v1/stock/movements")) {
                Map<String, String> query = query(uri.getRawQuery());
                return movementPage(Long.parseLong(query.get("productId")),
                        Integer.parseInt(query.getOrDefault("page", "0")),
                        Integer.parseInt(query.getOrDefault("size", "20")));
            }
            return respond(404, Map.of("message", "Not found"));
        } catch (RuntimeException e) {
            return respond(500, Map.of("message", String.valueOf(e)));
        }
    }

    private synchronized ResponseDefinition reserve(JsonNode body) {
        UUID id = UUID.fromString(body.path("reservationId").asString());
        Map<Long, Integer> lines = new TreeMap<>();
        body.path("lines").forEach(line -> lines.merge(line.path("productId").asLong(),
                line.path("quantity").asInt(), Integer::sum));
        Optional<ResponseDefinition> fault = fault(lines.keySet(), Operation.RESERVE);
        if (fault.isPresent()) {
            return fault.get();
        }
        Reservation prior = reservations.get(id);
        if (prior != null) {
            return prior.lines().equals(lines)
                    ? respond(201, Map.of("reservationId", id, "lines", prior.result()))
                    : respond(409, Map.of("message", "reservationId already used with different lines"));
        }
        for (Map.Entry<Long, Integer> line : lines.entrySet()) {
            Item current = resolve(line.getKey());
            if (current == null) {
                return respond(404, Map.of("message", "Product " + line.getKey() + " not found",
                        "productId", line.getKey()));
            }
            if (current.quantity() < line.getValue()) {
                return respond(409, Map.of("message", "Insufficient stock for product " + line.getKey(),
                        "productId", line.getKey()));
            }
        }
        String username = body.path("username").asString();
        List<Map<String, Object>> result = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        lines.forEach((productId, quantity) -> {
            Item after = write(productId, null, "ORDER_DEDUCTED", -quantity, items.get(productId).quantity(),
                    username, "Order deduction", null);
            ids.add(movements.getLast().id());
            result.add(level(productId, after, "quantityAfter"));
        });
        reservations.put(id, new Reservation(id, lines, username, "PENDING_CONFIRMATION", result, ids));
        return respond(201, Map.of("reservationId", id, "lines", result));
    }

    private synchronized ResponseDefinition confirm(UUID id, long orderId) {
        Reservation reservation = reservations.get(id);
        if (reservation == null) {
            return respond(404, Map.of("message", "Reservation not found"));
        }
        Optional<ResponseDefinition> fault = fault(reservation.lines().keySet(), Operation.CONFIRM);
        if (fault.isPresent()) {
            return fault.get();
        }
        if (reservation.status().equals("RELEASED")) {
            return respond(409, Map.of("message", "Reservation was released"));
        }
        reservations.put(id, reservation.withStatus("CONFIRMED"));
        for (int i = 0; i < movements.size(); i++) {
            Movement movement = movements.get(i);
            if (reservation.movementIds().contains(movement.id())) {
                movements.set(i, new Movement(movement.id(), movement.productId(), orderId, movement.type(),
                        movement.delta(), movement.quantityAfter(), movement.actor(), movement.reason(),
                        movement.requestId(), movement.createdAt(), movement.version()));
            }
        }
        return respond(200, Map.of("reservationId", id, "lines", reservation.result()));
    }

    private synchronized ResponseDefinition release(UUID id) {
        Reservation reservation = reservations.get(id);
        if (reservation == null) {
            return respond(404, Map.of("message", "Reservation not found"));
        }
        Optional<ResponseDefinition> fault = fault(reservation.lines().keySet(), Operation.RELEASE);
        if (fault.isPresent()) {
            return fault.get();
        }
        if (reservation.status().equals("CONFIRMED")) {
            return respond(409, Map.of("message", "Reservation is confirmed"));
        }
        if (reservation.status().equals("PENDING_CONFIRMATION")) {
            reservation.lines().forEach((productId, quantity) -> write(productId, null, "ORDER_RESTORED", quantity,
                    resolve(productId).quantity(), "system", "Reservation released", null));
            reservations.put(id, reservation.withStatus("RELEASED"));
        }
        return respond(200, Map.of("reservationId", id, "status", "RELEASED"));
    }

    private synchronized ResponseDefinition adjust(JsonNode body) {
        long productId = body.path("productId").asLong();
        Optional<ResponseDefinition> fault = fault(Set.of(productId), Operation.ADJUST);
        if (fault.isPresent()) {
            return fault.get();
        }
        Item current = resolve(productId);
        if (current == null) {
            return respond(404, Map.of("message", "Product not found"));
        }
        int delta = body.path("delta").asInt();
        String reason = body.path("reason").isNull() ? null : body.path("reason").asString();
        UUID requestId = UUID.fromString(body.path("requestId").asString());
        Optional<Movement> prior = movements.stream()
                .filter(movement -> movement.productId() == productId && requestId.equals(movement.requestId()))
                .findFirst();
        if (prior.isPresent()) {
            if (prior.get().delta() != delta || !java.util.Objects.equals(prior.get().reason(), reason)) {
                return respond(409, Map.of("message", "requestId already used with different payload"));
            }
            return respond(201, movementBody(prior.get(), true));
        }
        long result = (long) current.quantity() + delta;
        if (result < 0) {
            return respond(409, Map.of("message", "Insufficient stock"));
        }
        if (result > Integer.MAX_VALUE) {
            return respond(409, Map.of("message", "Inventory quantity exceeds supported maximum"));
        }
        write(productId, null, "ADMIN_ADJUSTMENT", delta, current.quantity(), body.path("actor").asString(),
                reason, requestId);
        return respond(201, movementBody(movements.getLast(), true));
    }

    private synchronized ResponseDefinition createItem(long productId, JsonNode body) {
        Optional<ResponseDefinition> fault = fault(Set.of(productId), Operation.ITEMS);
        if (fault.isPresent()) {
            return fault.get();
        }
        Optional<long[]> projected = projection.find(productId);
        Item existing = items.get(productId);
        long createdAt = projected.map(row -> row.length > 2 ? row[2] : 0L).orElse(0L);
        if (existing != null && existing.createdAt() == createdAt) {
            return respond(200, level(productId, existing, "quantity"));
        }
        movements.removeIf(movement -> movement.productId() == productId);
        long projectedVersion = projected.map(row -> row[1]).orElse(0L);
        int quantity = body.path("initialQuantity").asInt();
        items.put(productId, new Item(0, projectedVersion, createdAt));
        Item created = quantity > 0
                ? write(productId, null, "INITIAL_STOCK", quantity, 0, body.path("actor").asString(), "Initial stock",
                        null)
                : put(productId, 0, projectedVersion + 1);
        return respond(201, level(productId, created, "quantity"));
    }

    private synchronized ResponseDefinition deleteItem(long productId) {
        Optional<ResponseDefinition> fault = fault(Set.of(productId), Operation.ITEMS);
        if (fault.isPresent()) {
            return fault.get();
        }
        items.remove(productId);
        movements.removeIf(movement -> movement.productId() == productId);
        return responseDefinition().withStatus(204).build();
    }

    private synchronized ResponseDefinition getItem(long productId) {
        Item item = resolve(productId);
        return item == null
                ? respond(404, Map.of("message", "Product " + productId + " not found", "productId", productId))
                : respond(200, level(productId, item, "quantity"));
    }

    private synchronized ResponseDefinition movementPage(long productId, int page, int size) {
        Optional<ResponseDefinition> fault = fault(Set.of(productId), Operation.MOVEMENTS);
        if (fault.isPresent()) {
            return fault.get();
        }
        if (resolve(productId) == null) {
            return respond(404, Map.of("message", "Product " + productId + " not found", "productId", productId));
        }
        List<Movement> all = movements.stream().filter(movement -> movement.productId() == productId)
                .sorted(Comparator.comparing(Movement::createdAt).thenComparing(Movement::id).reversed())
                .toList();
        List<Map<String, Object>> content = all.stream().skip((long) page * size).limit(size)
                .map(movement -> movementBody(movement, false)).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        body.put("totalElements", all.size());
        body.put("totalPages", (all.size() + size - 1) / size);
        body.put("number", page);
        body.put("size", size);
        return respond(200, body);
    }

    /** The ledger item, seeded from or re-synchronised with the projection. Call while synchronized. */
    private Item resolve(long productId) {
        Item current = items.get(productId);
        Optional<long[]> projected = projection.find(productId);
        if (projected.isEmpty()) {
            return current;
        }
        int quantity = (int) projected.get()[0];
        long version = projected.get()[1];
        long createdAt = projected.get().length > 2 ? projected.get()[2] : 0;
        if (current != null && current.createdAt() != createdAt) {
            // Another product with a reused id: forget the old one.
            movements.removeIf(movement -> movement.productId() == productId);
            current = null;
        }
        if (current == null || version > current.version()
                || (version == current.version() && quantity != current.quantity())) {
            return put(productId, quantity, Math.max(version, current == null ? 0 : current.version()), createdAt);
        }
        return current;
    }

    private Item put(long productId, int quantity, long version) {
        Item current = items.get(productId);
        return put(productId, quantity, version, current == null ? 0 : current.createdAt());
    }

    private Item put(long productId, int quantity, long version, long createdAt) {
        Item item = new Item(quantity, version, createdAt);
        items.put(productId, item);
        return item;
    }

    private Item write(long productId, Long orderId, String type, int delta, int base, String actor, String reason,
                       UUID requestId) {
        Item current = items.get(productId);
        Item next = put(productId, base + delta, (current == null ? 0 : current.version()) + 1);
        movements.add(new Movement(movementIds.incrementAndGet(), productId, orderId, type, delta, next.quantity(),
                actor, reason, requestId, LocalDateTime.now(clock.withZone(ZoneOffset.UTC)).truncatedTo(ChronoUnit.MICROS),
                next.version()));
        return next;
    }

    private Optional<ResponseDefinition> fault(Set<Long> productIds, Operation operation) {
        for (Long productId : productIds) {
            Injected fault = faults.get(productId);
            if (fault != null && fault.operations().contains(operation)) {
                if (fault.reset()) {
                    return Optional.of(responseDefinition().withFault(Fault.CONNECTION_RESET_BY_PEER).build());
                }
                if (fault.delay() != null) {
                    return Optional.of(responseDefinition().withStatus(200).withFixedDelay((int) fault.delay().toMillis())
                            .withBody("{}").build());
                }
                return Optional.of(respond(fault.status(), Map.of("message", "Injected failure")));
            }
        }
        return Optional.empty();
    }

    private static Map<String, Object> level(long productId, Item item, String quantityField) {
        Map<String, Object> level = new LinkedHashMap<>();
        level.put("productId", productId);
        level.put(quantityField, item.quantity());
        level.put("ledgerVersion", item.version());
        return level;
    }

    private static Map<String, Object> movementBody(Movement movement, boolean withVersion) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", movement.id());
        body.put("productId", movement.productId());
        body.put("orderId", movement.orderId());
        body.put("type", movement.type());
        body.put("delta", movement.delta());
        body.put("quantityAfter", movement.quantityAfter());
        body.put("actor", movement.actor());
        body.put("reason", movement.reason());
        body.put("requestId", movement.requestId());
        body.put("createdAt", movement.createdAt().toString());
        if (withVersion) {
            body.put("ledgerVersion", movement.version());
        }
        return body;
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> query = new HashMap<>();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                int eq = pair.indexOf('=');
                query.put(eq < 0 ? pair : pair.substring(0, eq), eq < 0 ? "" : pair.substring(eq + 1));
            }
        }
        return query;
    }

    private ResponseDefinition respond(int status, Object body) {
        return responseDefinition()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(json.writeValueAsString(body))
                .build();
    }
}
