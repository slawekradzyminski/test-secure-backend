package com.awesome.testing.graphql;

import com.awesome.testing.dto.cart.CartItemDto;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CommerceGraphQlTest extends AbstractCommerceGraphQlTest {
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void requiresApplicationAccessToken() {
        // given
        var body = Map.of("query", "{ products { total } }");

        // when
        var anonymous = executePost(ENDPOINT, body, getJsonOnlyHeaders(), String.class);
        var invalid = executePost(ENDPOINT, body, getHeadersWith("invalid.invalid.invalid"), String.class);

        // then
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void readsCatalogWithVariablesAndPartialNotFoundError() {
        // given
        var product = product("999.99", 10);
        var customer = customer();

        // when
        var body = query(customer.token(), """
                query Catalog($id: ID!) {
                  product(id: $id) { id price }
                  missing: product(id: "9223372036854775807") { id }
                  products(limit: 1) { total offset limit items { id name } }
                }
                """, Map.of("id", product.getId()));

        // then
        assertThat(body.at("/data/product/id").asText()).isEqualTo(product.getId().toString());
        assertThat(body.at("/data/product/price").asText()).isEqualTo("999.99");
        assertThat(body.at("/data/products/items").size()).isEqualTo(1);
        assertThat(body.at("/data/products/total").asInt()).isEqualTo(1);
        assertThat(body.at("/data/missing").isNull()).isTrue();
        assertError(body, "NOT_FOUND");
        assertThat(body.at("/errors/0/path/0").asText()).isEqualTo("missing");
    }

    @Test
    void cartPreservesSnapshotPriceAfterCatalogChanges() {
        // given
        var customer = customer();
        var product = product("999.99", 10);
        cartService.addToCart(customer.username(), new CartItemDto(product.getId(), 3));
        product.setPrice(new BigDecimal("123.45"));
        productRepository.saveAndFlush(product);

        // when
        var cart = successful(customer, "{cart {totalItems totalPrice items {unitPrice product {id price}}}}",
                Map.of()).path("cart");
        var rest = mapper.readTree(executeGet(CART_ENDPOINT, getHeadersWith(customer.token()), String.class).getBody());

        // then
        assertThat(cart.at("/items/0/unitPrice").asText()).isEqualTo("999.99");
        assertThat(cart.at("/items/0/product/price").asText()).isEqualTo("123.45");
        assertThat(cart.get("totalPrice").asText()).isEqualTo("2999.97");
        assertThat(cart.get("totalItems").asInt()).isEqualTo(3);
        assertThat(rest.get("totalPrice").decimalValue()).isEqualByComparingTo("2999.97");
    }

    @Test
    void cartQueryCountDoesNotGrowWithItemCount() {
        // given
        var customer = customer();
        cartService.addToCart(customer.username(), new CartItemDto(product("12.30", 10).getId(), 1));
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        String document = "{cart {items {product {id name price}}}}";
        statistics.clear();
        var singleItemCart = successful(customer, document, Map.of());
        long singleItemQueries = statistics.getPrepareStatementCount();
        for (int i = 0; i < 4; i++) {
            cartService.addToCart(customer.username(), new CartItemDto(product("12.30", 10).getId(), 1));
        }
        statistics.clear();

        // when
        var largerCart = successful(customer, document, Map.of());
        long largerCartQueries = statistics.getPrepareStatementCount();

        // then
        assertThat(singleItemCart.at("/cart/items").size()).isEqualTo(1);
        assertThat(largerCart.at("/cart/items").size()).isEqualTo(5);
        assertThat(largerCartQueries).isEqualTo(singleItemQueries);
    }

    @Test
    void rejectsInvalidPaginationBounds() {
        // given
        var customer = customer();
        for (String arguments : List.of("limit: 101", "limit: 0", "offset: -1")) {
            // when
            var response = query(customer.token(), "{products(" + arguments + "){total}}", Map.of());

            // then
            assertError(response, "BAD_REQUEST");
        }
    }

    @Test
    void rejectsOversizedAdministrativePages() {
        // given
        var administrator = admin();
        var product = product("12.30", 5);
        var fields = List.of("carts(size:101)", "orders(size:101)", "inventory(size:101)",
                "inventoryMovements(productId:" + product.getId() + ",size:101)");

        for (String field : fields) {
            // when
            var response = query(administrator.token(), "{" + field + "{total}}", Map.of());

            // then
            assertError(response, "BAD_REQUEST");
        }
    }

    @Test
    void adminCanReadInventoryItemDetails() {
        // given
        var administrator = admin();
        var product = product("12.30", 5);

        // when
        var inventory = successful(administrator, """
                query InventoryItem($id: ID!) {
                  inventoryItem(productId: $id, lowStockThreshold: 10) {
                    productId name availableQuantity stockStatus lastChangedAt
                  }
                }
                """, Map.of("id", product.getId())).path("inventoryItem");

        // then
        assertThat(inventory.path("productId").asText()).isEqualTo(product.getId().toString());
        assertThat(inventory.path("name").asText()).isEqualTo(product.getName());
        assertThat(inventory.path("availableQuantity").asInt()).isEqualTo(5);
        assertThat(inventory.path("stockStatus").asText()).isEqualTo("LOW_STOCK");
        assertThat(inventory.path("lastChangedAt").asText()).isNotBlank();
    }

    @Test
    void rejectsExpensiveAliasesBeforeFetchingData() {
        // given
        var customer = customer();
        String fields = IntStream.range(0, 600).mapToObj(i -> "p" + i + ":product(id:1){id name}")
                .collect(Collectors.joining(" "));

        // when
        var response = executePost(ENDPOINT, Map.of("query", "{" + fields + "}"),
                getHeadersWith(customer.token()), String.class);
        var rejected = mapper.readTree(response.getBody());

        // then
        assertThat(rejected.path("errors").isEmpty()).isFalse();
        assertThat(rejected.path("data").isMissingNode() || rejected.path("data").isNull()).isTrue();
    }

    @Test
    void emptyCartAndPaginationBoundsAreStable() {
        // given
        var customer = customer();
        product("10.00", 10);
        var last = product("20.00", 10);

        // when
        var empty = successful(customer, "{cart {items {quantity} totalItems totalPrice}}", Map.of());
        var page = successful(customer, "{products(offset:1,limit:100){total offset limit items{id}}}", Map.of());

        // then
        assertThat(empty.at("/cart/items").isEmpty()).isTrue();
        assertThat(empty.at("/cart/totalItems").asInt()).isZero();
        assertThat(new BigDecimal(empty.at("/cart/totalPrice").asText())).isZero();
        assertThat(page.at("/products/offset").asInt()).isEqualTo(1);
        assertThat(page.at("/products/limit").asInt()).isEqualTo(100);
        assertThat(page.at("/products/items").size()).isEqualTo(1);
        assertThat(page.at("/products/items/0/id").asText()).isEqualTo(last.getId().toString());
    }

    @Test
    void rejectsOversizedBodiesAndHttpBatching() {
        // given
        var headers = getHeadersWith(customer().token());

        // when
        var oversized = executePost(ENDPOINT, Map.of("query", " ".repeat(65537)), headers, String.class);
        var batched = executePost(ENDPOINT, List.of(Map.of("query", "{cart {totalItems}}")), headers, String.class);

        // then
        assertThat(oversized.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(batched.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
