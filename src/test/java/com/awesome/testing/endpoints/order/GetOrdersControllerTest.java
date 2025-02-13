package com.awesome.testing.endpoints.order;

import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.service.CartService;
import com.awesome.testing.service.OrderService;
import com.awesome.testing.dto.order.OrderStatus;
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

class GetOrdersControllerTest extends AbstractEcommerceTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Test
    void shouldGetUserOrders() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        AddressDto testAddress = getRandomAddress();
        createOrder(client, testAddress);
        createOrder(client, testAddress);

        // when
        ResponseEntity<PageDto<OrderDto>> response = executeGet(
                ORDERS_ENDPOINT,
                getHeadersWith(clientToken),
                new ParameterizedTypeReference<>() {}
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(2);
        assertThat(response.getBody().getTotalElements()).isEqualTo(2);
    }

    @Test
    void shouldGetFilteredOrdersByStatus() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        AddressDto testAddress = getRandomAddress();
        
        OrderDto pendingOrder = createOrder(client, testAddress);
        OrderDto shippedOrder = createOrder(client, testAddress);
        orderService.updateOrderStatus(shippedOrder.getId(), OrderStatus.SHIPPED);
        OrderDto deliveredOrder = createOrder(client, testAddress);
        orderService.updateOrderStatus(deliveredOrder.getId(), OrderStatus.DELIVERED);

        // when
        ResponseEntity<PageDto<OrderDto>> response = executeGet(
                ORDERS_ENDPOINT + "?status=" + OrderStatus.SHIPPED,
                getHeadersWith(clientToken),
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
    void shouldGet401AsUnauthorized() {
        // when
        ResponseEntity<ErrorDto> response = executeGet(
                ORDERS_ENDPOINT,
                getJsonOnlyHeaders(),
                ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private OrderDto createOrder(UserRegisterDto client, AddressDto address) {
        ProductEntity testProduct = setupProduct();
        CartItemDto cartItemDto = getSingleCartItemFrom(testProduct.getId());
        cartService.addToCart(client.getUsername(), cartItemDto);
        return orderService.createOrder(client.getUsername(), address);
    }
}