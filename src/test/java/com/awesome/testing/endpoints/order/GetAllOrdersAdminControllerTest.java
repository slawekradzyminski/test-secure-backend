package com.awesome.testing.endpoints.order;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.service.CartService;
import com.awesome.testing.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static com.awesome.testing.factory.CartItemFactory.getSingleCartItemFrom;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class GetAllOrdersAdminControllerTest extends AbstractEcommerceTest {

    @Autowired
    private OrderService orderService;
    
    @Autowired
    private CartService cartService;

    private static final String ADMIN_ORDERS_ENDPOINT = "/api/orders/admin";

    @Test
    void shouldGetAllOrdersAsAdmin() {
        // given
        UserRegisterDto client1 = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        UserRegisterDto client2 = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);
        AddressDto testAddress = getRandomAddress();
        OrderDto order1 = createOrder(client1, testAddress);
        OrderDto order2 = createOrder(client1, testAddress);
        OrderDto order3 = createOrder(client2, testAddress);
        
        // when
        ResponseEntity<PageDto<OrderDto>> response = executeGet(
                ADMIN_ORDERS_ENDPOINT,
                getHeadersWith(adminToken),
                new ParameterizedTypeReference<>() {}
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(3);
        assertThat(response.getBody().getTotalElements()).isEqualTo(3);
    }

    @Test
    void shouldGetFilteredOrdersByStatusAsAdmin() {
        // given
        UserRegisterDto client1 = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        UserRegisterDto client2 = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);
        AddressDto testAddress = getRandomAddress();
        OrderDto pendingOrder = createOrder(client1, testAddress);
        OrderDto shippedOrder = createOrder(client1, testAddress);
        orderService.updateOrderStatus(shippedOrder.getId(), OrderStatus.SHIPPED);
        OrderDto deliveredOrder = createOrder(client2, testAddress);
        orderService.updateOrderStatus(deliveredOrder.getId(), OrderStatus.DELIVERED);

        // when
        ResponseEntity<PageDto<OrderDto>> response = executeGet(
                ADMIN_ORDERS_ENDPOINT + "?status=" + OrderStatus.SHIPPED,
                getHeadersWith(adminToken),
                new ParameterizedTypeReference<>() {}
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        assertThat(response.getBody().getTotalElements()).isEqualTo(1);
        assertThat(response.getBody().getContent().getFirst().getId()).isEqualTo(shippedOrder.getId());
        assertThat(response.getBody().getContent().getFirst().getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void shouldGet401AsAnonymous() {
        // when
        ResponseEntity<ErrorDto> response = executeGet(
                ADMIN_ORDERS_ENDPOINT,
                getJsonOnlyHeaders(),
                ErrorDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldGet403AsClient() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);

        // when
        ResponseEntity<ErrorDto> response = executeGet(
                ADMIN_ORDERS_ENDPOINT,
                getHeadersWith(clientToken),
                ErrorDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private OrderDto createOrder(UserRegisterDto client, AddressDto address) {
        ProductEntity testProduct = setupProduct();
        CartItemDto cartItemDto = getSingleCartItemFrom(testProduct.getId());
        cartService.addToCart(client.getUsername(), cartItemDto);
        return orderService.createOrder(client.getUsername(), address);
    }
} 