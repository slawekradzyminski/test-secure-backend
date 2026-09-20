package com.awesome.testing.traffic.protocol;

import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import org.springframework.context.annotation.Profile;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
@Profile("graphql")
public class GraphQlTrafficInterceptor implements WebGraphQlInterceptor {
    private static final Set<String> FIELDS = Set.of("products", "product", "cart", "carts", "orders", "order",
            "inventory", "inventoryItem", "inventoryMovements", "addCartItem", "updateCartItem", "removeCartItem",
            "clearCart", "checkout", "cancelOrder", "updateOrderStatus", "createProduct", "updateProduct",
            "deleteProduct", "adjustInventory", "__schema", "__type", "__typename");
    private static final Set<String> CODES = Set.of("BAD_REQUEST", "FORBIDDEN", "UNAUTHORIZED", "NOT_FOUND",
            "CONFLICT", "INTERNAL_ERROR", "VALIDATIONERROR", "INVALIDSYNTAX", "EXECUTIONABORTED");

    @Override
    @SuppressWarnings("unchecked")
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        Object attribute = request.getAttributes().get(GraphQlTrafficFilter.ATTRIBUTE);
        if (!(attribute instanceof AtomicReference<?>)) {
            return chain.next(request);
        }
        AtomicReference<GraphQlTrafficFilter.Summary> summary = (AtomicReference<GraphQlTrafficFilter.Summary>) attribute;
        String operation = operation(request);
        return chain.next(request).doOnNext(response -> {
            String outcome = response.getErrors().isEmpty() ? "SUCCESS" : response.getData() == null ? "ERROR" : "PARTIAL_ERROR";
            List<String> codes = response.getErrors().stream().map(error -> {
                Object code = error.getExtensions().getOrDefault("code", error.getErrorType());
                String value = String.valueOf(code).toUpperCase(Locale.ROOT);
                return CODES.contains(value) ? value : "GRAPHQL_ERROR";
            }).distinct().sorted().limit(8).toList();
            summary.set(new GraphQlTrafficFilter.Summary(operation, outcome, codes));
        });
    }

    private String operation(WebGraphQlRequest request) {
        try {
            Document document = Parser.parse(request.getDocument());
            List<OperationDefinition> operations = document.getDefinitionsOfType(OperationDefinition.class);
            OperationDefinition selected = request.getOperationName() == null && operations.size() == 1 ? operations.getFirst()
                    : operations.stream().filter(operation -> request.getOperationName() != null
                            && request.getOperationName().equals(operation.getName())).findFirst().orElse(null);
            if (selected == null) {
                return "unresolved";
            }
            String fields = selected.getSelectionSet().getSelections().stream().filter(Field.class::isInstance)
                    .map(Field.class::cast).map(Field::getName).filter(FIELDS::contains)
                    .distinct().sorted().limit(8).collect(Collectors.joining(","));
            return selected.getOperation().name().toLowerCase(Locale.ROOT) + (fields.isEmpty() ? "" : " " + fields);
        } catch (RuntimeException invalidDocument) {
            return "invalid";
        }
    }
}
