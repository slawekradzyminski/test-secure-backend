package com.awesome.testing.endpoints.order;

import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.ProductEntity;
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

class GetSingleOrderControllerTest extends AbstractEcommerceTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Test
    void shouldGetOrderById() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        AddressDto testAddress = getRandomAddress();
        OrderDto createdOrder = createOrder(client, testAddress);

        // when
        ResponseEntity<OrderDto> response = executeGet(
                ORDERS_ENDPOINT + "/" + createdOrder.getId(),
                getHeadersWith(clientToken),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(createdOrder.getId());
        assertThat(response.getBody().getUsername()).isEqualTo(client.getUsername());
    }


    @Test
    void shouldAllowAdminToAccessAnyOrder() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);
        AddressDto testAddress = getRandomAddress();
        OrderDto clientOrder = createOrder(client, testAddress);

        // when
        ResponseEntity<OrderDto> response = executeGet(
                ORDERS_ENDPOINT + "/" + clientOrder.getId(),
                getHeadersWith(adminToken),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(clientOrder.getId());
        assertThat(response.getBody().getUsername()).isEqualTo(client.getUsername());
    }

    @Test
    void shouldGet401WhenNoAuthorizationHeader() {
        // when
        ResponseEntity<OrderDto> response = executeGet(
                ORDERS_ENDPOINT + "/1",
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
        ResponseEntity<OrderDto> response = executeGet(
                ORDERS_ENDPOINT + "/999999",
                getHeadersWith(clientToken),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldGet404WhenClientTriesToAccessOtherUsersOrder() {
        // given
        UserRegisterDto client1 = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        UserRegisterDto client2 = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String client2Token = getToken(client2);
        AddressDto testAddress = getRandomAddress();
        OrderDto client1Order = createOrder(client1, testAddress);

        // when
        ResponseEntity<OrderDto> response = executeGet(
                ORDERS_ENDPOINT + "/" + client1Order.getId(),
                getHeadersWith(client2Token),
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