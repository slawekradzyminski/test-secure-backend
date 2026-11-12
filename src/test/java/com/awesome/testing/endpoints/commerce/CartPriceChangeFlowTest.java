package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.cart.UpdateCartItemDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.entity.ProductEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-023: the cart keeps, and checkout charges, the unit price of the moment a line was added or updated
 * (the course contract's price snapshot); a line whose product price changed since then is flagged.
 */
class CartPriceChangeFlowTest extends AbstractCommerceFlowTest {

    @Test
    void theCartFlagsAChangedPriceAndCheckoutChargesTheCartPrice() {
        // given
        ProductEntity product = product("10.00", 10);
        addToCart(clientToken, product, 2);
        changePrice(product, "20.00");

        // when
        Map<String, Object> cart = cart();
        OrderDto order = checkout(clientToken);

        // then
        assertThat(new BigDecimal(cart.get("totalPrice").toString())).isEqualByComparingTo("20.00");
        assertThat(line(cart)).containsEntry("productId", product.getId().intValue())
                .containsEntry("quantity", 2)
                .containsEntry("priceChanged", true);
        assertThat(new BigDecimal(line(cart).get("unitPrice").toString())).isEqualByComparingTo("10.00");
        assertThat(new BigDecimal(line(cart).get("currentPrice").toString())).isEqualByComparingTo("20.00");
        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getUnitPrice()).isEqualByComparingTo("10.00");
            assertThat(item.getTotalPrice()).isEqualByComparingTo("20.00");
        });
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal(cart.get("totalPrice").toString()));
    }

    @Test
    void anUnchangedLineKeepsTheOriginalShape() {
        // given
        ProductEntity product = product("10.00", 10);
        addToCart(clientToken, product, 1);

        // when
        Map<String, Object> cart = cart();

        // then
        assertThat(line(cart)).containsOnlyKeys("productId", "quantity");
        assertThat(new BigDecimal(cart.get("totalPrice").toString())).isEqualByComparingTo("10.00");
    }

    @Test
    void updatingTheLineTakesTheCurrentPriceAndClearsTheFlag() {
        // given
        ProductEntity product = product("10.00", 10);
        addToCart(clientToken, product, 3);
        changePrice(product, "8.00");
        Map<String, Object> flagged = cart();

        // when
        ResponseEntity<Map> updated = executePut(CART_ITEMS_ENDPOINT + "/" + product.getId(),
                new UpdateCartItemDto(3), getHeadersWith(clientToken), Map.class);

        // then
        assertThat(line(flagged)).containsEntry("priceChanged", true);
        assertThat(new BigDecimal(flagged.get("totalPrice").toString())).isEqualByComparingTo("30.00");
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(line(updated.getBody())).containsOnlyKeys("productId", "quantity");
        assertThat(new BigDecimal(updated.getBody().get("totalPrice").toString())).isEqualByComparingTo("24.00");
    }

    @Test
    void priceChangeFieldsInARequestAreIgnored() {
        // given
        ProductEntity product = product("10.00", 10);

        // when
        ResponseEntity<Map> added = executePost(CART_ITEMS_ENDPOINT,
                Map.of("productId", product.getId(), "quantity", 1, "priceChanged", true,
                        "unitPrice", 1, "currentPrice", 1),
                getHeadersWith(clientToken), Map.class);

        // then
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(line(added.getBody())).containsOnlyKeys("productId", "quantity");
        assertThat(new BigDecimal(added.getBody().get("totalPrice").toString())).isEqualByComparingTo("10.00");
    }

    private void changePrice(ProductEntity product, String price) {
        ProductEntity stored = productRepository.findById(product.getId()).orElseThrow();
        stored.setPrice(new BigDecimal(price));
        productRepository.save(stored);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cart() {
        ResponseEntity<Map> response = executeGet(CART_ENDPOINT, getHeadersWith(clientToken), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> line(Map<?, ?> cart) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) cart.get("items");
        assertThat(items).hasSize(1);
        return items.getFirst();
    }
}
