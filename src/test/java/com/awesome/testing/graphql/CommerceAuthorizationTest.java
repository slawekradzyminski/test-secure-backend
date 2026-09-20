package com.awesome.testing.graphql;

import com.awesome.testing.dto.cart.CartItemDto;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommerceAuthorizationTest extends AbstractCommerceGraphQlTest {
    @Test
    void customersSeeOnlyTheirCartWhileAdminCanReadAndListBoth() {
        // given
        var alice = customer();
        var bob = customer();
        var administrator = admin();
        var first = product("10.25", 10);
        var second = product("20.50", 10);
        cartService.addToCart(alice.username(), new CartItemDto(first.getId(), 2));
        cartService.addToCart(bob.username(), new CartItemDto(second.getId(), 1));
        String selection = "{cart {username totalPrice items {product {id}}}}";
        String otherCart = "query($owner:String){cart(username:$owner){username items {product {id}}}}";

        // when
        var own = successful(alice, selection, Map.of());
        var forbidden = query(alice.token(), otherCart, Map.of("owner", bob.username()));
        var adminCart = successful(administrator, otherCart, Map.of("owner", bob.username()));
        var allCarts = successful(administrator, "{carts {total items {username items {product {id}}}}}", Map.of());
        var customerListing = query(alice.token(), "{carts {total}}", Map.of());

        // then
        assertThat(own.at("/cart/username").asText()).isEqualTo(alice.username());
        assertThat(own.at("/cart/items").size()).isEqualTo(1);
        assertThat(own.at("/cart/items/0/product/id").asText()).isEqualTo(first.getId().toString());
        assertError(forbidden, "FORBIDDEN");
        assertThat(forbidden.at("/data/cart").isNull()).isTrue();
        assertThat(adminCart.at("/cart/username").asText()).isEqualTo(bob.username());
        assertThat(adminCart.at("/cart/items/0/product/id").asText()).isEqualTo(second.getId().toString());
        assertThat(allCarts.at("/carts/total").asInt()).isEqualTo(2);
        assertThat(allCarts.at("/carts/items")).extracting(node -> node.path("username").asText())
                .containsExactlyInAnyOrder(alice.username(), bob.username());
        assertError(customerListing, "FORBIDDEN");
    }

    @Test
    void customersSeeOnlyTheirOrdersWhileAdminCanReadAndFilterEveryOrder() {
        // given
        var alice = customer();
        var bob = customer();
        var administrator = admin();
        var first = givenOrder(alice, product("10.25", 10));
        var second = givenOrder(bob, product("20.50", 10));
        String detail = "query($id:ID!){order(id:$id){id username shippingAddress {street} totalAmount}}";
        String listing = "query($owner:String){orders(username:$owner){total items {id username}}}";

        // when
        var own = successful(alice, listing, Map.of());
        var hidden = query(alice.token(), detail, Map.of("id", second.getId()));
        var forbidden = query(alice.token(), listing, Map.of("owner", bob.username()));
        var all = successful(administrator, listing, Map.of());
        var filtered = successful(administrator, listing, Map.of("owner", bob.username()));
        var adminDetail = successful(administrator, detail, Map.of("id", second.getId()));

        // then
        assertThat(own.at("/orders/total").asInt()).isEqualTo(1);
        assertThat(own.at("/orders/items/0/id").asText()).isEqualTo(first.getId().toString());
        assertError(hidden, "NOT_FOUND");
        assertThat(hidden.at("/data/order").isNull()).isTrue();
        assertError(forbidden, "FORBIDDEN");
        assertThat(all.at("/orders/total").asInt()).isEqualTo(2);
        assertThat(filtered.at("/orders/total").asInt()).isEqualTo(1);
        assertThat(filtered.at("/orders/items/0/id").asText()).isEqualTo(second.getId().toString());
        assertThat(adminDetail.at("/order/username").asText()).isEqualTo(bob.username());
        assertThat(adminDetail.at("/order/shippingAddress/street").asText()).isEqualTo(ADDRESS.getStreet());
    }

    @Test
    void inventoryManagementIsAdminOnlyWhileCatalogStockIsShared() {
        // given
        var customer = customer();
        var administrator = admin();
        var product = product("10.25", 7);
        String document = """
                query($id:ID!) {
                  product(id:$id) {id stockQuantity}
                  inventory {total items {productId availableQuantity stockStatus lastChangedAt}}
                  inventoryItem(productId:$id) {availableQuantity}
                  inventoryMovements(productId:$id) {total}
                }
                """;

        // when
        var denied = query(customer.token(), document, Map.of("id", product.getId()));
        var allowed = successful(administrator, document, Map.of("id", product.getId()));

        // then
        assertThat(denied.at("/data/product/stockQuantity").asInt()).isEqualTo(7);
        assertThat(denied.path("errors").size()).isEqualTo(3);
        assertThat(denied.path("errors")).allSatisfy(error ->
                assertThat(error.at("/extensions/code").asText()).isEqualTo("FORBIDDEN"));
        assertThat(denied.at("/data/inventory").isNull()).isTrue();
        assertThat(denied.at("/data/inventoryItem").isNull()).isTrue();
        assertThat(denied.at("/data/inventoryMovements").isNull()).isTrue();
        assertThat(allowed.at("/inventory/items/0/productId").asText()).isEqualTo(product.getId().toString());
        assertThat(allowed.at("/inventory/items/0/availableQuantity").asInt()).isEqualTo(7);
        assertThat(allowed.at("/inventory/items/0/stockStatus").asText()).isEqualTo("LOW_STOCK");
        assertThat(allowed.at("/inventory/items/0/lastChangedAt").asText()).isNotBlank();
    }

    @Test
    void customerCannotMutateAnotherCartOrCancelAnotherOrder() {
        // given
        var alice = customer();
        var bob = customer();
        var product = product("10.25", 10);
        var order = givenOrder(bob, product);
        cartService.addToCart(bob.username(), new CartItemDto(product.getId(), 2));
        String document = """
                mutation($owner:String,$product:ID!,$order:ID!) {
                  addCartItem(username:$owner,productId:$product,quantity:1){totalItems}
                  updateCartItem(username:$owner,productId:$product,quantity:5){totalItems}
                  removeCartItem(username:$owner,productId:$product){totalItems}
                  clearCart(username:$owner){totalItems}
                  cancelOrder(id:$order){id}
                }
                """;

        // when
        var response = query(alice.token(), document,
                Map.of("owner", bob.username(), "product", product.getId(), "order", order.getId()));

        // then
        assertThat(response.path("errors").size()).isEqualTo(5);
        assertThat(response.path("errors")).allSatisfy(error ->
                assertThat(error.at("/extensions/code").asText()).isEqualTo("FORBIDDEN"));
        assertThat(cartService.getCart(bob.username()).getTotalItems()).isEqualTo(2);
        assertThat(orderService.getOrder(bob.username(), order.getId()).getStatus().name()).isEqualTo("PENDING");
    }

    @Test
    void customerCannotUseAdministrativeMutations() {
        // given
        var customer = customer();
        var owner = customer();
        var product = product("10.25", 10);
        var order = givenOrder(owner, product);
        String document = """
                mutation($product:ID!,$order:ID!) {
                  createProduct(input:{name:"Forbidden product",description:"Must not be created",price:"10.00",stockQuantity:1,category:"Test"}){id}
                  updateProduct(id:$product,input:{price:"1.00"}){id}
                  deleteProduct(id:$product)
                  updateOrderStatus(id:$order,status:PAID){id}
                  adjustInventory(productId:$product,input:{delta:1,reason:"Forbidden",requestId:"21c6341c-7e16-4a18-86d1-0a4c700223a3"}){id}
                }
                """;

        // when
        var response = query(customer.token(), document, Map.of("product", product.getId(), "order", order.getId()));

        // then
        assertThat(response.path("errors").size()).isEqualTo(5);
        assertThat(response.path("errors")).allSatisfy(error ->
                assertThat(error.at("/extensions/code").asText()).isEqualTo("FORBIDDEN"));
        assertThat(productRepository.count()).isEqualTo(1);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getPrice()).isEqualByComparingTo("10.25");
        assertThat(orderService.getOrder(owner.username(), order.getId()).getStatus().name()).isEqualTo("PENDING");
    }
}
