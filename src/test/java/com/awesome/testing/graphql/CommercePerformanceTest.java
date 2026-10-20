package com.awesome.testing.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.entity.OrderItemEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.traffic.TrafficLogService;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.UUID;

class CommercePerformanceTest extends AbstractCommerceGraphQlTest {
    @Autowired private EntityManagerFactory entityManagerFactory;
    // Asynchronous telemetry writes must not contribute to the SQL measurements.
    @MockitoBean private TrafficLogService traffic;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void cartQueryCountDoesNotGrowWithItemCount() {
        // given
        var customer = customer();
        cartService.addToCart(customer.username(), new CartItemDto(product("12.30", 10).getId(), 1));
        var statistics = statistics();
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
    void productPageUsesOneSelectAndOneCountWithoutRefetchingProducts() {
        var actor = customer();
        var first = product("10.00", 1);
        first.setImageUrl("https://example.test/product.png");
        productRepository.saveAndFlush(first);
        product("20.00", 1);
        statistics().clear();

        var page =
                successful(
                        actor,
                        "{products(limit:1){total items{id name description price category imageUrl}}}",
                        Map.of());

        assertThat(page.at("/products/total").asInt()).isEqualTo(2);
        assertThat(page.at("/products/items/0/name").asString()).isEqualTo(first.getName());
        assertThat(page.at("/products/items/0/category").asString()).isEqualTo(first.getCategory());
        assertThat(page.at("/products/items/0/imageUrl").asString()).isEqualTo(first.getImageUrl());
        assertThat(statistics().getEntityStatistics(ProductEntity.class.getName()).getLoadCount()).isEqualTo(1);
        // One authentication lookup, one product select and one count.
        assertThat(statistics().getQueryExecutionCount()).isEqualTo(3);
    }

    @Test
    void aliasedFragmentCountWithSkippedItemsDoesNotLoadProducts() {
        var actor = customer();
        product("10.00", 1);
        statistics().clear();

        var page =
                successful(
                        actor,
                        """
                query($skip:Boolean!){ products { ...Count items @skip(if:$skip){id} } }
                fragment Count on ProductPage { number:total }
                """,
                        Map.of("skip", true));

        assertThat(page.at("/products/number").asInt()).isEqualTo(1);
        assertThat(statistics().getEntityStatistics(ProductEntity.class.getName()).getLoadCount()).isZero();
        assertThat(statistics().getQueryExecutionCount()).isEqualTo(2);
    }

    @Test
    void productItemsWithoutTotalDoNotRunCountQuery() {
        var actor = customer();
        product("10.00", 1);
        product("20.00", 1);
        statistics().clear();

        var page = successful(actor, "{products(limit:1){items{id}}}", Map.of());

        assertThat(page.at("/products/items").size()).isEqualTo(1);
        assertThat(statistics().getQueryExecutionCount()).isEqualTo(2);
    }

    @Test
    void orderCountsAndSummariesDoNotLoadOrderItems() {
        var actor = customer();
        givenOrder(actor, product("10.00", 5));
        statistics().clear();

        var counts = successful(actor, "{orders{total}}", Map.of());
        assertThat(counts.at("/orders/total").asInt()).isEqualTo(1);
        assertThat(statistics().getEntityStatistics(OrderItemEntity.class.getName()).getLoadCount()).isZero();
        assertThat(statistics().getQueryExecutionCount()).isEqualTo(2);
        statistics().clear();

        var summaries =
                successful(
                        actor,
                        "{orders{items{id username status totalAmount shippingAddress{city} createdAt updatedAt}}}",
                        Map.of());
        assertThat(summaries.at("/orders/items").size()).isEqualTo(1);
        assertThat(summaries.at("/orders/items/0/username").asString()).isEqualTo(actor.username());
        assertThat(summaries.at("/orders/items/0/status").asString()).isEqualTo("PENDING");
        assertThat(summaries.at("/orders/items/0/totalAmount").asString()).isEqualTo("10.00");
        assertThat(summaries.at("/orders/items/0/shippingAddress/city").asString()).isEqualTo("Warsaw");
        assertThat(summaries.at("/orders/items/0/id").asString()).isNotBlank();
        assertThat(summaries.at("/orders/items/0/createdAt").asString()).isNotBlank();
        assertThat(summaries.at("/orders/items/0/updatedAt").asString()).isNotBlank();
        assertThat(statistics().getEntityStatistics(OrderItemEntity.class.getName()).getLoadCount()).isZero();
        assertThat(statistics().getQueryExecutionCount()).isEqualTo(2);
    }

    @Test
    void orderItemQueryCountDoesNotGrowWithDistinctProducts() {
        var actor = customer();
        givenOrder(actor, product("10.00", 5));
        statistics().clear();
        successful(actor, "{orders{items{items{productName}}}}", Map.of());
        long small = statistics().getQueryExecutionCount();
        for (int i = 0; i < 4; i++) {
            givenOrder(actor, product("12.00", 5));
        }
        statistics().clear();

        var orders = successful(actor, "{orders{items{items{productName}}}}", Map.of());

        assertThat(orders.at("/orders/items").size()).isEqualTo(5);
        assertThat(statistics().getQueryExecutionCount()).isEqualTo(small);
    }

    @Test
    void selectedOrderItemsPreserveIdsNamesQuantitiesAndMoneyOnAPaginatedResponse() {
        var actor = customer();
        var first = givenOrder(actor, product("12.30", 5));
        givenOrder(actor, product("45.60", 5));

        var page =
                successful(
                        actor,
                        """
                {orders(page:1,size:1){page size total items{id items{id productId productName quantity unitPrice totalPrice}}}}
                """,
                        Map.of());

        assertThat(page.at("/orders/page").asInt()).isEqualTo(1);
        assertThat(page.at("/orders/size").asInt()).isEqualTo(1);
        assertThat(page.at("/orders/total").asInt()).isEqualTo(2);
        assertThat(page.at("/orders/items").size()).isEqualTo(1);
        assertThat(page.at("/orders/items/0/id").asString()).isNotEqualTo(first.getId().toString());
        assertThat(page.at("/orders/items/0/items").size()).isEqualTo(1);
        var item = page.at("/orders/items/0/items/0");
        assertThat(item.path("id").asString()).isNotEqualTo("0");
        assertThat(item.path("productId").asString()).isNotEqualTo("0");
        assertThat(item.path("productName").asString()).isNotBlank();
        assertThat(item.path("quantity").asInt()).isEqualTo(1);
        assertThat(item.path("unitPrice").asString()).isEqualTo("45.60");
        assertThat(item.path("totalPrice").asString()).isEqualTo("45.60");
    }

    @Test
    void inventoryMovementPaginationHonorsTheRequestedPageAndSize() {
        var administrator = admin();
        var product = product("12.30", 5);
        for (int i = 0; i < 2; i++) {
            successful(
                    administrator,
                    """
                    mutation($id:ID!,$request:String!){adjustInventory(productId:$id,
                    input:{delta:1,reason:"pagination",requestId:$request}){id}}
                    """,
                    Map.of("id", product.getId(), "request", UUID.randomUUID().toString()));
        }

        var page =
                successful(
                        administrator,
                        "query($id:ID!){inventoryMovements(productId:$id,page:1,size:1){page size total items{id}}}",
                        Map.of("id", product.getId()));

        assertThat(page.at("/inventoryMovements/page").asInt()).isEqualTo(1);
        assertThat(page.at("/inventoryMovements/size").asInt()).isEqualTo(1);
        assertThat(page.at("/inventoryMovements/total").asInt()).isEqualTo(2);
        assertThat(page.at("/inventoryMovements/items").size()).isEqualTo(1);
    }

    @Test
    void countOnlyOrdersStillEnforceOwnershipAndAdminRoles() {
        var actor = customer();
        var other = customer();
        givenOrder(other, product("10.00", 5));

        var own = successful(actor, "{orders{total}}", Map.of());
        var forbidden =
                query(
                        actor.token(),
                        "query($owner:String){orders(username:$owner){total}}",
                        Map.of("owner", other.username()));
        var inventory = query(actor.token(), "{inventory{total}}", Map.of());

        assertThat(own.at("/orders/total").asInt()).isZero();
        assertError(forbidden, "FORBIDDEN");
        assertError(inventory, "FORBIDDEN");
    }
}
