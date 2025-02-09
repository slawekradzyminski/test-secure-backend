package com.awesome.testing.service;

import com.awesome.testing.dto.CartDTO;
import com.awesome.testing.dto.CartItemDTO;
import com.awesome.testing.model.CartItem;
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
    public CartDTO getCart(String username) {
        List<CartItem> cartItems = cartItemRepository.findByUsername(username);
        return createCartDTO(username, cartItems);
    }

    @Transactional
    public CartDTO addToCart(String username, CartItemDTO cartItemDTO) {
        ProductEntity product = productRepository.findById(cartItemDTO.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        CartItem cartItem = cartItemRepository.findByUsernameAndProductId(username, cartItemDTO.getProductId())
                .map(existingItem -> updateItem(cartItemDTO, existingItem, product))
                .orElseGet(() -> createItem(username, cartItemDTO, product));

        cartItemRepository.save(cartItem);
        List<CartItem> cartItems = cartItemRepository.findByUsername(username);
        return createCartDTO(username, cartItems);
    }

    private CartItem createItem(String username, CartItemDTO cartItemDTO, ProductEntity product) {
        return CartItem.builder()
                .username(username)
                .product(product)
                .quantity(cartItemDTO.getQuantity())
                .price(product.getPrice())
                .version(0L)
                .build();
    }

    private CartItem updateItem(CartItemDTO cartItemDTO, CartItem existingItem, ProductEntity product) {
        existingItem.setQuantity(existingItem.getQuantity() + cartItemDTO.getQuantity());
        existingItem.setPrice(product.getPrice());
        return existingItem;
    }

    @Transactional
    public CartDTO updateCartItem(String username, Long productId, CartItemDTO cartItemDTO) {
        CartItem cartItem = cartItemRepository.findByUsernameAndProductId(username, productId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        cartItem.setQuantity(cartItemDTO.getQuantity());
        cartItem.setPrice(cartItem.getProduct().getPrice());
        cartItemRepository.save(cartItem);

        List<CartItem> cartItems = cartItemRepository.findByUsername(username);
        return createCartDTO(username, cartItems);
    }

    @Transactional
    public CartDTO removeFromCart(String username, Long productId) {
        cartItemRepository.deleteByUsernameAndProductId(username, productId);
        List<CartItem> cartItems = cartItemRepository.findByUsername(username);
        return createCartDTO(username, cartItems);
    }

    @Transactional
    public void clearCart(String username) {
        cartItemRepository.deleteByUsername(username);
    }

    private CartDTO createCartDTO(String username, List<CartItem> cartItems) {
        return CartDTO.builder()
                .username(username)
                .items(getItems(cartItems))
                .totalPrice(calculateTotalPrice(cartItems))
                .totalItems(calculateTotalItems(cartItems))
                .build();
    }

    private List<CartItemDTO> getItems(List<CartItem> cartItems) {
        return cartItems.stream()
                .map(this::convertToCartItemDTO)
                .toList();
    }

    private CartItemDTO convertToCartItemDTO(CartItem item) {
        return CartItemDTO.builder()
                .productId(item.getProduct().getId())
                .quantity(item.getQuantity())
                .build();
    }

    private int calculateTotalItems(List<CartItem> cartItems) {
        return cartItems.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    private BigDecimal calculateTotalPrice(List<CartItem> cartItems) {
        return cartItems.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
} 