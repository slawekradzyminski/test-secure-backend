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
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public CartDto getCart(String username) {
        List<CartItemEntity> cartItems = cartItemRepository.findByUsername(username);
        return CartDto.builder()
                .username(username)
                .items(cartItems.stream().map(this::convertToCartItemDto).toList())
                .totalPrice(calculateTotalPrice(cartItems))
                .totalItems(cartItems.stream().mapToInt(CartItemEntity::getQuantity).sum())
                .build();
    }

    @Transactional
    public CartDto addToCart(String username, CartItemDto cartItemDto) {
        ProductEntity product = productRepository.findById(cartItemDto.getProductId())
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));

        CartItemEntity cartItem = cartItemRepository.findByUsernameAndProductId(username, cartItemDto.getProductId())
                .map(existingItem -> updateItem(cartItemDto, existingItem, product))
                .orElseGet(() -> createItem(username, cartItemDto, product));

        inventoryService.checkAvailable(product, cartItem.getQuantity());
        cartItemRepository.save(cartItem);
        return getCart(username);
    }

    @Transactional
    public CartDto updateCartItem(String username, Long productId, UpdateCartItemDto updateCartItemDto) {
        CartItemEntity cartItem = getCartItemEntity(username, productId);

        int quantity = updateCartItemDto.getQuantity();
        if (quantity == 0) {
            cartItemRepository.delete(cartItem);
            return getCart(username);
        }

        cartItem.setQuantity(quantity);
        inventoryService.checkAvailable(cartItem.getProduct(), quantity);
        cartItem.setPrice(cartItem.getProduct().getPrice());
        cartItemRepository.save(cartItem);

        return getCart(username);
    }

    @Transactional
    public CartDto removeFromCart(String username, Long productId) {
        getCartItemEntity(username, productId);

        cartItemRepository.deleteByUsernameAndProductId(username, productId);
        return getCart(username);
    }

    @Transactional
    public void clearCart(String username) {
        cartItemRepository.deleteByUsername(username);
    }

    private CartItemEntity getCartItemEntity(String username, Long productId) {
        return cartItemRepository.findByUsernameAndProductId(username, productId)
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found"));
    }

    private CartItemEntity createItem(String username, CartItemDto cartItemDto, ProductEntity product) {
        return CartItemEntity.builder()
                .username(username)
                .product(product)
                .quantity(cartItemDto.getQuantity())
                .price(product.getPrice())
                .version(0L)
                .build();
    }

    private CartItemEntity updateItem(CartItemDto cartItemDto, CartItemEntity existingItem, ProductEntity product) {
        existingItem.setQuantity(existingItem.getQuantity() + cartItemDto.getQuantity());
        existingItem.setPrice(product.getPrice());
        return existingItem;
    }

    private CartItemDto convertToCartItemDto(CartItemEntity item) {
        return CartItemDto.builder()
                .productId(item.getProduct().getId())
                .quantity(item.getQuantity())
                .build();
    }

    private BigDecimal calculateTotalPrice(List<CartItemEntity> cartItems) {
        return cartItems.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

} 
