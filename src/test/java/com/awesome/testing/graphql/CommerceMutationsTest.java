package com.awesome.testing.graphql;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommerceMutationsTest extends AbstractCommerceGraphQlTest {
    @Test
    void completesShoppingFlowWithStockDeductionAndOneTimeRestoration() {
        // given
        var customer = customer();
        var product = product("19.99", 10);
        successful(customer, "mutation($id:ID!){addCartItem(productId:$id,quantity:2){totalItems}}",
                Map.of("id", product.getId()));

        // when
        var checkout = successful(customer, """
                mutation($address:AddressInput!){checkout(address:$address){id status totalAmount items {unitPrice quantity}}}
                """, Map.of("address", ADDRESS_INPUT)).path("checkout");
        var stockAfterCheckout = productRepository.findById(product.getId()).orElseThrow().getStockQuantity();
        var cartAfterCheckout = cartService.getCart(customer.username());
        var cancelled = successful(customer, "mutation($id:ID!){cancelOrder(id:$id){id status}}",
                Map.of("id", checkout.path("id").asText()));
        var repeated = query(customer.token(), "mutation($id:ID!){cancelOrder(id:$id){id}}",
                Map.of("id", checkout.path("id").asText()));

        // then
        assertThat(checkout.path("status").asText()).isEqualTo("PENDING");
        assertThat(checkout.path("totalAmount").asText()).isEqualTo("39.98");
        assertThat(checkout.at("/items/0/unitPrice").asText()).isEqualTo("19.99");
        assertThat(stockAfterCheckout).isEqualTo(8);
        assertThat(cartAfterCheckout.getTotalItems()).isZero();
        assertThat(cancelled.at("/cancelOrder/status").asText()).isEqualTo("CANCELLED");
        assertError(repeated, "BAD_REQUEST");
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(orderService.getOrder(customer.username(), checkout.path("id").asLong()).getTotalAmount())
                .isEqualByComparingTo("39.98");
    }

    @Test
    void supportsCartUpdateRemovalAndClearingWithoutReservingStock() {
        // given
        var customer = customer();
        var first = product("10.25", 10);
        var second = product("20.50", 10);
        successful(customer, "mutation($a:ID!,$b:ID!){a:addCartItem(productId:$a,quantity:1){totalItems} b:addCartItem(productId:$b,quantity:1){totalItems}}",
                Map.of("a", first.getId(), "b", second.getId()));

        // when
        var updated = successful(customer, "mutation($id:ID!){updateCartItem(productId:$id,quantity:3){totalItems totalPrice}}",
                Map.of("id", first.getId()));
        var removed = successful(customer, "mutation($id:ID!){removeCartItem(productId:$id){totalItems}}",
                Map.of("id", second.getId()));
        var cleared = successful(customer, "mutation{clearCart{totalItems items {quantity}}}", Map.of());

        // then
        assertThat(updated.at("/updateCartItem/totalItems").asInt()).isEqualTo(4);
        assertThat(updated.at("/updateCartItem/totalPrice").asText()).isEqualTo("51.25");
        assertThat(removed.at("/removeCartItem/totalItems").asInt()).isEqualTo(3);
        assertThat(cleared.path("clearCart").isObject()).isTrue();
        assertThat(cleared.at("/clearCart/totalItems").asInt()).isZero();
        assertThat(cleared.at("/clearCart/items").isEmpty()).isTrue();
        assertThat(productRepository.findById(first.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void adminCanManageProductsAndRetryInventoryAdjustmentSafely() {
        // given
        var administrator = admin();
        var created = successful(administrator, """
                mutation {createProduct(input:{name:"GraphQL product",description:"Contract fixture",price:"12.34",stockQuantity:5,category:"Training"}){id price}}
                """, Map.of()).path("createProduct");
        String id = created.path("id").asText();
        String requestId = UUID.randomUUID().toString();
        String adjustment = """
                mutation($id:ID!,$input:InventoryAdjustmentInput!){adjustInventory(productId:$id,input:$input){id quantityAfter requestId actor type}}
                """;
        var input = Map.of("delta", 3, "reason", "Restock", "requestId", requestId);

        // when
        var adjusted = successful(administrator, adjustment, Map.of("id", id, "input", input)).path("adjustInventory");
        var replayed = successful(administrator, adjustment, Map.of("id", id, "input", input)).path("adjustInventory");
        var conflict = query(administrator.token(), adjustment, Map.of("id", id, "input",
                Map.of("delta", 4, "reason", "Restock", "requestId", requestId)));
        var updated = successful(administrator, "mutation($id:ID!){updateProduct(id:$id,input:{price:\"15.50\",description:null}){price description}}", Map.of("id", id));
        var history = successful(administrator, "query($id:ID!){inventoryMovements(productId:$id){total items {type delta}}}", Map.of("id", id));
        var deleted = successful(administrator, "mutation($id:ID!){deleteProduct(id:$id)}", Map.of("id", id));

        var deletedAgain = successful(administrator, "mutation($id:ID!){deleteProduct(id:$id)}", Map.of("id", id));

        // then
        assertThat(created.path("price").asText()).isEqualTo("12.34");
        assertThat(adjusted.path("quantityAfter").asInt()).isEqualTo(8);
        assertThat(adjusted.path("actor").asText()).isEqualTo(administrator.username());
        assertThat(replayed).isEqualTo(adjusted);
        assertError(conflict, "CONFLICT");
        assertThat(updated.at("/updateProduct/price").asText()).isEqualTo("15.50");
        assertThat(updated.at("/updateProduct/description").asText()).isEqualTo("Contract fixture");
        assertThat(history.at("/inventoryMovements/total").asInt()).isEqualTo(2);
        assertThat(deleted.path("deleteProduct").asBoolean()).isTrue();
        assertThat(deletedAgain.path("deleteProduct").asBoolean()).isFalse();
        assertThat(productRepository.existsById(Long.valueOf(id))).isFalse();
    }

    @Test
    void rejectsInvalidMutationInputsWithoutChangingState() {
        // given
        var customer = customer();
        var administrator = admin();
        var product = product("10.25", 1);

        // when
        var invalidQuantity = query(customer.token(), "mutation($id:ID!){addCartItem(productId:$id,quantity:0){totalItems}}", Map.of("id", product.getId()));
        var unavailable = query(customer.token(), "mutation($id:ID!){addCartItem(productId:$id,quantity:2){totalItems}}", Map.of("id", product.getId()));
        var invalidAddress = query(customer.token(), "mutation{checkout(address:{street:\"\",city:\"\",state:\"\",zipCode:\"bad\",country:\"\"}){id}}", Map.of());
        var invalidPrice = query(administrator.token(), "mutation($id:ID!){updateProduct(id:$id,input:{price:\"1.234\"}){id}}", Map.of("id", product.getId()));
        var invalidAdjustment = query(administrator.token(), "mutation($id:ID!){adjustInventory(productId:$id,input:{delta:0,reason:\"\",requestId:\"21c6341c-7e16-4a18-86d1-0a4c700223a3\"}){id}}", Map.of("id", product.getId()));

        // then
        assertError(invalidQuantity, "BAD_REQUEST");
        assertError(unavailable, "CONFLICT");
        assertError(invalidAddress, "BAD_REQUEST");
        assertError(invalidPrice, "BAD_REQUEST");
        assertError(invalidAdjustment, "BAD_REQUEST");
        assertThat(cartService.getCart(customer.username()).getTotalItems()).isZero();
        assertThat(orderRepository.count()).isZero();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getPrice()).isEqualByComparingTo("10.25");
    }

    @Test
    void adminCanManageAnotherCustomersOrderAndCart() {
        // given
        var customer = customer();
        var administrator = admin();
        var product = product("10.25", 10);
        var order = givenOrder(customer, product);

        // when
        var paid = successful(administrator, "mutation($id:ID!){updateOrderStatus(id:$id,status:PAID){status}}", Map.of("id", order.getId()));
        var cancelled = successful(administrator, "mutation($id:ID!){cancelOrder(id:$id){status username}}", Map.of("id", order.getId()));
        var cart = successful(administrator, "mutation($id:ID!,$owner:String){addCartItem(productId:$id,quantity:2,username:$owner){username totalItems}}", Map.of("id", product.getId(), "owner", customer.username()));

        // then
        assertThat(paid.at("/updateOrderStatus/status").asText()).isEqualTo("PAID");
        assertThat(cancelled.at("/cancelOrder/status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.at("/cancelOrder/username").asText()).isEqualTo(customer.username());
        assertThat(cart.at("/addCartItem/username").asText()).isEqualTo(customer.username());
        assertThat(cart.at("/addCartItem/totalItems").asInt()).isEqualTo(2);
    }
}
