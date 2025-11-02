package com.awesome.testing.endpoints.order;

import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.service.CartService;
import com.awesome.testing.dto.order.OrderItemDto;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static com.awesome.testing.factory.CartItemFactory.getSingleCartItemFrom;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class CreateOrderControllerTest extends AbstractEcommerceTest {

    @Autowired
    private CartService cartService;

    @Test
    void shouldCreateOrder() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        AddressDto testAddress = getRandomAddress();
        ProductEntity product = addToCart(client);

        // when
        ResponseEntity<OrderDto> response = executePost(
                ORDERS_ENDPOINT,
                testAddress,
                getHeadersWith(clientToken),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        log.info(response.getBody().toString());

        assertOrderDto(response.getBody(), client, testAddress, product);
    }           

    @Test
    void shouldReturn400WhenAddressIsInvalid() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        AddressDto invalidAddress = new AddressDto();
        addToCart(client);

        // when
        ResponseEntity<OrderDto> response = executePost(
                ORDERS_ENDPOINT,
                invalidAddress,
                getHeadersWith(clientToken),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn400WhenCartIsEmpty() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        AddressDto testAddress = getRandomAddress();

        // when
        ResponseEntity<OrderDto> response = executePost(
                ORDERS_ENDPOINT,
                testAddress,
                getHeadersWith(clientToken),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn401WhenNoAuthorizationHeader() {
        // given
        AddressDto testAddress = getRandomAddress();

        // when
        ResponseEntity<OrderDto> response = executePost(
                ORDERS_ENDPOINT,
                testAddress,
                getJsonOnlyHeaders(),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ProductEntity addToCart(UserRegisterDto client) {
        ProductEntity testProduct = setupProduct();
        CartItemDto cartItemDto = getSingleCartItemFrom(testProduct.getId());
        cartService.addToCart(client.getUsername(), cartItemDto);
        return testProduct;
    }

    private void assertOrderDto(OrderDto order, UserRegisterDto client, AddressDto expectedAddress, ProductEntity product) {
        assertThat(order.getId()).isGreaterThan(0);
        assertThat(order.getUsername()).isEqualTo(client.getUsername());
        assertThat(order.getItems()).hasSize(1);
        OrderItemDto item = order.getItems().getFirst();
        assertThat(item.getId()).isGreaterThan(0);
        assertThat(item.getProductId()).isEqualTo(product.getId());
        assertThat(item.getProductName()).isEqualTo(product.getName());
        assertThat(item.getQuantity()).isEqualTo(1);
        assertThat(item.getUnitPrice()).isEqualTo(product.getPrice());
        assertThat(item.getTotalPrice()).isEqualTo(product.getPrice());
        assertThat(order.getTotalAmount()).isEqualTo(product.getPrice());
        assertThat(order.getStatus().name()).isEqualTo("PENDING");
        AddressDto shippingAddress = order.getShippingAddress();
        assertThat(shippingAddress.getStreet()).isEqualTo(expectedAddress.getStreet());
        assertThat(shippingAddress.getCity()).isEqualTo(expectedAddress.getCity());
        assertThat(shippingAddress.getState()).isEqualTo(expectedAddress.getState());
        assertThat(shippingAddress.getZipCode()).isEqualTo(expectedAddress.getZipCode());
        assertThat(shippingAddress.getCountry()).isEqualTo(expectedAddress.getCountry());
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();
    }
}