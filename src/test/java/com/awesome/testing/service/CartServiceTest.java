package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CartItemNotFoundException;
import com.awesome.testing.controller.exception.ProductNotFoundException;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.cart.UpdateCartItemDto;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final String USERNAME = "johndoe";

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

    private ProductEntity product;
    private CartItemEntity cartItem;

    @BeforeEach
    void setUp() {
        product = ProductEntity.builder()
                .id(1L)
                .name("Laptop")
                .price(BigDecimal.valueOf(1000))
                .stockQuantity(5)
                .description("Gaming")
                .category("Electronics")
                .build();

        cartItem = CartItemEntity.builder()
                .id(10L)
                .username(USERNAME)
                .product(product)
                .quantity(1)
                .price(product.getPrice())
                .build();
    }

    @Test
    void shouldGetCartForUser() {
        when(cartItemRepository.findByUsername(USERNAME)).thenReturn(List.of(cartItem));

        CartDto cart = cartService.getCart(USERNAME);

        assertThat(cart.getUsername()).isEqualTo(USERNAME);
        assertThat(cart.getTotalItems()).isEqualTo(1);
        assertThat(cart.getTotalPrice()).isEqualTo(product.getPrice());
    }

    @Test
    void shouldAddNewItemToCart() {
        CartItemDto dto = CartItemDto.builder()
                .productId(product.getId())
                .quantity(2)
                .build();
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(cartItemRepository.findByUsernameAndProductId(USERNAME, product.getId())).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItemEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartItemRepository.findByUsername(USERNAME)).thenReturn(List.of(
                CartItemEntity.builder()
                        .username(USERNAME)
                        .product(product)
                        .quantity(2)
                        .price(product.getPrice())
                        .build()
        ));

        CartDto cart = cartService.addToCart(USERNAME, dto);

        assertThat(cart.getTotalItems()).isEqualTo(2);
        assertThat(cart.getItems()).hasSize(1);
        verify(cartItemRepository).save(any(CartItemEntity.class));
    }

    @Test
    void shouldIncreaseQuantityWhenItemAlreadyExists() {
        CartItemDto dto = CartItemDto.builder()
                .productId(product.getId())
                .quantity(3)
                .build();
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(cartItemRepository.findByUsernameAndProductId(USERNAME, product.getId()))
                .thenReturn(Optional.of(cartItem));
        when(cartItemRepository.findByUsername(USERNAME)).thenReturn(List.of(cartItem));

        CartDto cart = cartService.addToCart(USERNAME, dto);

        assertThat(cartItem.getQuantity()).isEqualTo(4);
        verify(cartItemRepository).save(cartItem);
        assertThat(cart.getTotalItems()).isEqualTo(4);
    }

    @Test
    void shouldThrowWhenAddingUnknownProduct() {
        CartItemDto dto = CartItemDto.builder()
                .productId(999L)
                .quantity(1)
                .build();
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addToCart(USERNAME, dto))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void shouldUpdateCartItemQuantity() {
        UpdateCartItemDto dto = UpdateCartItemDto.builder().quantity(5).build();
        when(cartItemRepository.findByUsernameAndProductId(USERNAME, product.getId()))
                .thenReturn(Optional.of(cartItem));
        when(cartItemRepository.findByUsername(USERNAME)).thenReturn(List.of(cartItem));

        CartDto cart = cartService.updateCartItem(USERNAME, product.getId(), dto);

        assertThat(cartItem.getQuantity()).isEqualTo(5);
        assertThat(cart.getTotalItems()).isEqualTo(5);
        verify(cartItemRepository).save(cartItem);
    }

    @Test
    void shouldDeleteCartItemWhenQuantityBecomesZero() {
        UpdateCartItemDto dto = UpdateCartItemDto.builder().quantity(0).build();
        when(cartItemRepository.findByUsernameAndProductId(USERNAME, product.getId()))
                .thenReturn(Optional.of(cartItem));
        when(cartItemRepository.findByUsername(USERNAME)).thenReturn(List.of());

        CartDto cart = cartService.updateCartItem(USERNAME, product.getId(), dto);

        assertThat(cart.getTotalItems()).isZero();
        verify(cartItemRepository).delete(cartItem);
    }

    @Test
    void shouldThrowWhenUpdatingMissingItem() {
        when(cartItemRepository.findByUsernameAndProductId(USERNAME, product.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateCartItem(USERNAME, product.getId(),
                UpdateCartItemDto.builder().quantity(1).build()))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    void shouldRemoveItemFromCart() {
        when(cartItemRepository.findByUsernameAndProductId(USERNAME, product.getId()))
                .thenReturn(Optional.of(cartItem));
        when(cartItemRepository.findByUsername(USERNAME)).thenReturn(List.of());

        CartDto cart = cartService.removeFromCart(USERNAME, product.getId());

        assertThat(cart.getTotalItems()).isZero();
        verify(cartItemRepository).deleteByUsernameAndProductId(USERNAME, product.getId());
    }

    @Test
    void shouldClearCart() {
        cartService.clearCart(USERNAME);

        verify(cartItemRepository).deleteByUsername(USERNAME);
    }
}
