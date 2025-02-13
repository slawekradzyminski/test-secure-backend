package com.awesome.testing.endpoints.order;

import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.dto.order.OrderStatus;
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

class UpdateOrderStatusControllerTest extends AbstractEcommerceTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Test
    void shouldUpdateOrderStatusAsAdmin() {
        // given
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);
        OrderDto createdOrder = createOrder();

        // when
        ResponseEntity<OrderDto> response = executePut(
                ORDERS_ENDPOINT + "/" + createdOrder.getId() + "/status",
                OrderStatus.SHIPPED,
                getHeadersWith(adminToken),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(createdOrder.getId());
        assertThat(response.getBody().getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void shouldGet400WhenInvalidStatusTransition() {
        // given
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);
        OrderDto createdOrder = createOrder();
        orderService.updateOrderStatus(createdOrder.getId(), OrderStatus.DELIVERED);

        // when - try to cancel a delivered order
        ResponseEntity<OrderDto> response = executePut(
                ORDERS_ENDPOINT + "/" + createdOrder.getId() + "/status",
                OrderStatus.CANCELLED,
                getHeadersWith(adminToken),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldGet401WhenNoAuthorizationHeader() {
        // when
        ResponseEntity<OrderDto> response = executePut(
                ORDERS_ENDPOINT + "/1/status",
                OrderStatus.SHIPPED,
                getJsonOnlyHeaders(),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldGet403WhenNotAdmin() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        OrderDto createdOrder = createOrder();

        // when
        ResponseEntity<OrderDto> response = executePut(
                ORDERS_ENDPOINT + "/" + createdOrder.getId() + "/status",
                OrderStatus.SHIPPED,
                getHeadersWith(clientToken),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldGet404WhenOrderNotFound() {
        // given
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);

        // when
        ResponseEntity<OrderDto> response = executePut(
                ORDERS_ENDPOINT + "/999999/status",
                OrderStatus.SHIPPED,
                getHeadersWith(adminToken),
                OrderDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private OrderDto createOrder() {
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        AddressDto testAddress = getRandomAddress();
        ProductEntity testProduct = setupProduct();
        CartItemDto cartItemDto = getSingleCartItemFrom(testProduct.getId());
        cartService.addToCart(client.getUsername(), cartItemDto);
        return orderService.createOrder(client.getUsername(), testAddress);
    }
} 