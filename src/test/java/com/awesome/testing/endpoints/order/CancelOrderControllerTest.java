package com.awesome.testing.endpoints.order;

import com.awesome.testing.dto.AddressDto;
import com.awesome.testing.dto.OrderDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.model.OrderStatus;
import com.awesome.testing.model.ProductEntity;
import com.awesome.testing.service.CartService;
import com.awesome.testing.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static com.awesome.testing.factory.CartItemFactory.getSingleCartItemFrom;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class CancelOrderControllerTest extends AbstractEcommerceTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Test
    void shouldCancelOrder() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        AddressDto testAddress = getRandomAddress();
        OrderDto createdOrder = createOrder(client, testAddress);

        // when
        ResponseEntity<OrderDto> response = executePost(
                ORDERS_ENDPOINT + "/" + createdOrder.getId() + "/cancel",
                null,
                getHeadersWith(clientToken),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(createdOrder.getId());
        assertThat(response.getBody().getStatus().name()).isEqualTo("CANCELLED");
    }

    @Test
    void shouldGet400WhenCancellingDeliveredOrder() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        AddressDto testAddress = getRandomAddress();
        OrderDto createdOrder = createOrder(client, testAddress);
        orderService.updateOrderStatus(createdOrder.getId(), OrderStatus.DELIVERED);

        // when
        ResponseEntity<OrderDto> response = executePost(
                ORDERS_ENDPOINT + "/" + createdOrder.getId() + "/cancel",
                null,
                getHeadersWith(clientToken),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldGet401WhenNoAuthorizationHeader() {
        // when
        ResponseEntity<OrderDto> response = executePost(
                ORDERS_ENDPOINT + "/1/cancel",
                null,
                getJsonOnlyHeaders(),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldGet404WhenOrderNotFound() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);

        // when
        ResponseEntity<OrderDto> response = executePost(
                ORDERS_ENDPOINT + "/999999/cancel",
                null,
                getHeadersWith(clientToken),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private OrderDto createOrder(UserRegisterDto client, AddressDto address) {
        ProductEntity testProduct = setupProduct();
        CartItemDto cartItemDto = getSingleCartItemFrom(testProduct.getId());
        cartService.addToCart(client.getUsername(), cartItemDto);
        return orderService.createOrder(client.getUsername(), address);
    }
} 