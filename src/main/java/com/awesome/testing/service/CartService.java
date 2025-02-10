package com.awesome.testing.service;

import com.awesome.testing.controller.exception.CartItemNotFoundException;
import com.awesome.testing.controller.exception.ProductNotFoundException;
import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.cart.UpdateCartItemDto;
import com.awesome.testing.model.CartItemEntity;
import com.awesome.testing.model.ProductEntity;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public CartDto getCart(String username) {
        List<CartItemEntity> cartItems = cartItemRepository.findByUsername(username);
        return createCartDto(username, cartItems);
    }

    @Transactional
    public CartDto addToCart(String username, CartItemDto cartItemDto) {
        ProductEntity product = productRepository.findById(cartItemDto.getProductId())
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));

        CartItemEntity cartItem = cartItemRepository.findByUsernameAndProductId(username, cartItemDto.getProductId())
                .map(existingItem -> updateItem(cartItemDto, existingItem, product))
                .orElseGet(() -> createItem(username, cartItemDto, product));

        cartItemRepository.save(cartItem);
        List<CartItemEntity> cartItems = cartItemRepository.findByUsername(username);
        return createCartDto(username, cartItems);
    }

    @Transactional
    public CartDto updateCartItem(String username, Long productId, UpdateCartItemDto updateCartItemDto) {
        CartItemEntity cartItem = cartItemRepository.findByUsernameAndProductId(username, productId)
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found"));

        cartItem.setQuantity(updateCartItemDto.getQuantity());
        cartItem.setPrice(cartItem.getProduct().getPrice());
        cartItemRepository.save(cartItem);

        List<CartItemEntity> cartItems = cartItemRepository.findByUsername(username);
        return createCartDto(username, cartItems);
    }

    @Transactional
    public CartDto removeFromCart(String username, Long productId) {
        cartItemRepository.findByUsernameAndProductId(username, productId)
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found"));

        cartItemRepository.deleteByUsernameAndProductId(username, productId);
        List<CartItemEntity> cartItems = cartItemRepository.findByUsername(username);
        return createCartDto(username, cartItems);
    }

    @Transactional
    public void clearCart(String username) {
        cartItemRepository.deleteByUsername(username);
    }

    private CartDto createCartDto(String username, List<CartItemEntity> cartItems) {
        return CartDto.builder()
                .username(username)
                .items(getItems(cartItems))
                .totalPrice(calculateTotalPrice(cartItems))
                .totalItems(calculateTotalItems(cartItems))
                .build();
    }

    private CartItemEntity createItem(String username, CartItemDto cartItemDTO, ProductEntity product) {
        return CartItemEntity.builder()
                .username(username)
                .product(product)
                .quantity(cartItemDTO.getQuantity())
                .price(product.getPrice())
                .version(0L)
                .build();
    }

    private CartItemEntity updateItem(CartItemDto cartItemDto, CartItemEntity existingItem, ProductEntity product) {
        existingItem.setQuantity(existingItem.getQuantity() + cartItemDto.getQuantity());
        existingItem.setPrice(product.getPrice());
        return existingItem;
    }

    private List<CartItemDto> getItems(List<CartItemEntity> cartItems) {
        return cartItems.stream()
                .map(this::convertToCartItemDto)
                .toList();
    }

    private CartItemDto convertToCartItemDto(CartItemEntity item) {
        return CartItemDto.builder()
                .productId(item.getProduct().getId())
                .quantity(item.getQuantity())
                .build();
    }

    private int calculateTotalItems(List<CartItemEntity> cartItems) {
        return cartItems.stream()
                .mapToInt(CartItemEntity::getQuantity)
                .sum();
    }

    private BigDecimal calculateTotalPrice(List<CartItemEntity> cartItems) {
        return cartItems.stream()
                .map(this::multiplyByQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal multiplyByQuantity(CartItemEntity item) {
        return item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }
} 