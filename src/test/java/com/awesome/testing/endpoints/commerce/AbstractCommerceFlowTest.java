package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.fulfilment.CreateShipmentDto;
import com.awesome.testing.dto.fulfilment.PackShipmentDto;
import com.awesome.testing.dto.fulfilment.ShipShipmentDto;
import com.awesome.testing.dto.fulfilment.ShipmentDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.fulfilment.Carrier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

/** Shared actors and shop flows for the commerce and fulfilment endpoint tests. */
abstract class AbstractCommerceFlowTest extends AbstractEcommerceTest {

    protected static final String WAREHOUSE = "/api/v1/warehouse";

    protected UserRegisterDto client;
    protected String clientToken;
    protected String otherClientToken;
    protected String adminToken;
    protected String warehouseToken;

    @BeforeEach
    void createActors() {
        client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        clientToken = getToken(client);
        otherClientToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        adminToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_ADMIN)));
        warehouseToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_WAREHOUSE)));
    }

    protected ProductEntity product(String price, int stock) {
        return productRepository.save(ProductEntity.builder()
                .name("Commerce product " + System.nanoTime())
                .description("Product used by commerce endpoint tests")
                .price(new BigDecimal(price))
                .stockQuantity(stock)
                .category("Tests")
                .build());
    }

    protected void addToCart(String token, ProductEntity product, int quantity) {
        ResponseEntity<String> response = executePost(CART_ITEMS_ENDPOINT,
                CartItemDto.builder().productId(product.getId()).quantity(quantity).build(),
                getHeadersWith(token), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    protected OrderDto checkout(String token) {
        ResponseEntity<OrderDto> response = executePost(ORDERS_ENDPOINT, getRandomAddress(), getHeadersWith(token), OrderDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    protected OrderDto order(String token, ProductEntity product, int quantity) {
        addToCart(token, product, quantity);
        return checkout(token);
    }

    protected void markPaid(Long orderId) {
        ResponseEntity<OrderDto> response = executePut(ORDERS_ENDPOINT + "/" + orderId + "/status",
                OrderStatus.PAID, getHeadersWith(adminToken), OrderDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    protected ShipmentDto releaseToWarehouse(Long orderId) {
        markPaid(orderId);
        ResponseEntity<ShipmentDto> response = executePost(WAREHOUSE + "/shipments",
                new CreateShipmentDto(orderId), getHeadersWith(warehouseToken), ShipmentDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    protected ResponseEntity<ShipmentDto> transition(Long shipmentId, String action, Object body) {
        return executePost(WAREHOUSE + "/shipments/" + shipmentId + "/" + action, body,
                getHeadersWith(warehouseToken), ShipmentDto.class);
    }

    /** Drives an order through the whole warehouse flow and returns the delivered shipment. */
    protected ShipmentDto deliver(Long orderId) {
        ShipmentDto shipment = releaseToWarehouse(orderId);
        assertThat(transition(shipment.getId(), "pick", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transition(shipment.getId(), "pack", new PackShipmentDto(new BigDecimal("1.50")))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transition(shipment.getId(), "ship", new ShipShipmentDto(Carrier.DHL, "1234567890"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<ShipmentDto> delivered = transition(shipment.getId(), "deliver", null);
        assertThat(delivered.getStatusCode()).isEqualTo(HttpStatus.OK);
        return delivered.getBody();
    }

    protected int stockOf(ProductEntity product) {
        return productRepository.findById(product.getId()).orElseThrow().getStockQuantity();
    }
}
