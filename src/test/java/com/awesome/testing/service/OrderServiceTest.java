package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.*;
import com.awesome.testing.entity.inventory.InventoryState;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final String USERNAME = "johndoe";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private OrderService orderService;

    private AddressDto addressDto;
    private CartItemEntity cartItem;

    @BeforeEach
    void setUp() {
        addressDto = AddressDto.builder()
                .street("Main St")
                .city("City")
                .state("State")
                .zipCode("12-345")
                .country("PL")
                .build();

        ProductEntity product = ProductEntity.builder()
                .id(1L)
                .name("Laptop")
                .price(BigDecimal.valueOf(1000))
                .description("Gaming")
                .stockQuantity(10)
                .category("Electronics")
                .build();

        cartItem = CartItemEntity.builder()
                .id(1L)
                .username(USERNAME)
                .product(product)
                .quantity(2)
                .price(product.getPrice())
                .build();
    }

    @Test
    void shouldCreateOrderFromCart() {
        when(cartItemRepository.findByUsernameForUpdate(USERNAME)).thenReturn(List.of(cartItem));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(cartItem.getProduct()));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity order = invocation.getArgument(0);
            order.setId(5L);
            return order;
        });

        OrderDto order = orderService.createOrder(USERNAME, addressDto);

        assertThat(order.getId()).isEqualTo(5L);
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getTotalAmount()).isEqualTo(BigDecimal.valueOf(2000));
        verify(orderRepository).save(argThat(saved -> saved.getInventoryState() == InventoryState.DEDUCTED));
        verify(inventoryService).checkAvailable(cartItem.getProduct(), 2);
        verify(inventoryService).deduct(eq(cartItem.getProduct()), eq(2), any(OrderEntity.class));
        verify(cartItemRepository).deleteByUsername(USERNAME);
    }

    @Test
    void shouldThrowWhenCartIsEmpty() {
        when(cartItemRepository.findByUsernameForUpdate(USERNAME)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.createOrder(USERNAME, addressDto))
                .isInstanceOf(CustomException.class)
                .hasMessage("Cart is empty");
    }

    @Test
    void shouldUpdateOrderStatus() {
        OrderEntity entity = OrderEntity.builder()
                .id(10L)
                .status(OrderStatus.PENDING)
                .shippingAddress(AddressEntity.from(addressDto))
                .totalAmount(BigDecimal.valueOf(100))
                .build();
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(entity));
        when(orderRepository.save(entity)).thenReturn(entity);

        OrderDto dto = orderService.updateOrderStatus(10L, OrderStatus.SHIPPED);

        assertThat(dto.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void shouldForbidCancellingWhenStatusNotCancellable() {
        OrderEntity entity = OrderEntity.builder()
                .id(11L)
                .status(OrderStatus.SHIPPED)
                .shippingAddress(AddressEntity.from(addressDto))
                .build();
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> orderService.updateOrderStatus(11L, OrderStatus.CANCELLED))
                .isInstanceOf(CustomException.class)
                .hasMessage("Order cannot be cancelled in current status");
    }

    @Test
    void shouldCancelOrderWhenAdmin() {
        OrderEntity entity = OrderEntity.builder()
                .id(12L)
                .username("owner")
                .status(OrderStatus.PENDING)
                .shippingAddress(AddressEntity.from(addressDto))
                .build();
        when(orderRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(entity));
        when(orderRepository.save(entity)).thenReturn(entity);

        OrderDto dto = orderService.cancelOrder(12L, "other", true);

        assertThat(dto.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldRestoreDeductedInventoryExactlyOnceWhenOrderIsCancelled() {
        ProductEntity product = cartItem.getProduct();
        OrderEntity entity = OrderEntity.builder()
                .id(14L)
                .username(USERNAME)
                .status(OrderStatus.PENDING)
                .inventoryState(InventoryState.DEDUCTED)
                .shippingAddress(AddressEntity.from(addressDto))
                .items(List.of(OrderItemEntity.builder()
                        .product(product)
                        .quantity(2)
                        .price(product.getPrice())
                        .build()))
                .build();
        when(orderRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(entity));
        when(productRepository.findByIdForUpdate(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.save(entity)).thenReturn(entity);

        OrderDto cancelled = orderService.cancelOrder(14L, USERNAME, false);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(entity.getInventoryState()).isEqualTo(InventoryState.RESTORED);
        verify(inventoryService).restore(product, 2, entity);

        assertThatThrownBy(() -> orderService.cancelOrder(14L, USERNAME, false))
                .isInstanceOf(CustomException.class)
                .hasMessage("Order cannot be cancelled in current status");
        verify(inventoryService).restore(product, 2, entity);
    }

    @Test
    void shouldNotChangeStockWhenAdminCancelsALegacyOrder() {
        OrderEntity entity = OrderEntity.builder()
                .id(15L)
                .username(USERNAME)
                .status(OrderStatus.PAID)
                .inventoryState(InventoryState.LEGACY_UNTRACKED)
                .shippingAddress(AddressEntity.from(addressDto))
                .build();
        when(orderRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(entity));
        when(orderRepository.save(entity)).thenReturn(entity);

        OrderDto cancelled = orderService.updateOrderStatus(15L, OrderStatus.CANCELLED);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(entity.getInventoryState()).isEqualTo(InventoryState.LEGACY_UNTRACKED);
        verifyNoInteractions(productRepository, inventoryService);
    }

    @Test
    void shouldThrowWhenCancellingForeignOrderWithoutAdminRights() {
        OrderEntity entity = OrderEntity.builder()
                .id(13L)
                .username("owner")
                .status(OrderStatus.PENDING)
                .shippingAddress(AddressEntity.from(addressDto))
                .build();
        when(orderRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> orderService.cancelOrder(13L, "attacker", false))
                .isInstanceOf(CustomException.class)
                .hasMessage("You cannot cancel someone else's order");
    }

    @Test
    void shouldGetUserOrdersWithStatusFilter() {
        Page<OrderEntity> orders = new PageImpl<>(List.of(OrderEntity.builder()
                .id(1L)
                .status(OrderStatus.PENDING)
                .shippingAddress(AddressEntity.from(addressDto))
                .build()));
        when(orderRepository.findByUsernameAndStatus(eq(USERNAME), eq(OrderStatus.PENDING), any(PageRequest.class)))
                .thenReturn(orders);

        Page<OrderDto> result = orderService.getUserOrders(USERNAME, OrderStatus.PENDING, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(orderRepository, never()).findByUsername(eq(USERNAME), any());
    }

    @Test
    void shouldGetOrderForOwner() {
        OrderEntity entity = OrderEntity.builder()
                .id(20L)
                .username(USERNAME)
                .status(OrderStatus.PENDING)
                .shippingAddress(AddressEntity.from(addressDto))
                .build();
        when(orderRepository.findByIdAndUsername(20L, USERNAME)).thenReturn(Optional.of(entity));

        OrderDto dto = orderService.getOrder(USERNAME, 20L);

        assertThat(dto.getId()).isEqualTo(20L);
    }

    @Test
    void shouldGetOrderByIdForAdmin() {
        OrderEntity entity = OrderEntity.builder()
                .id(21L)
                .status(OrderStatus.PENDING)
                .shippingAddress(AddressEntity.from(addressDto))
                .build();
        when(orderRepository.findById(21L)).thenReturn(Optional.of(entity));

        OrderDto dto = orderService.getOrderById(21L);

        assertThat(dto.getId()).isEqualTo(21L);
    }
}
