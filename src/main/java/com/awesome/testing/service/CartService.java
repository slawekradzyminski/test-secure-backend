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
import com.awesome.testing.service.coupon.CouponPreview;
import com.awesome.testing.service.coupon.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final CouponService couponService;

    @Transactional(readOnly = true)
    public CartDto getCart(String username) {
        List<CartItemEntity> cartItems = cartItemRepository.findByUsername(username);
        BigDecimal subtotal = calculateTotalPrice(cartItems);
        Optional<CouponPreview> coupon = couponService.preview(username, subtotal);
        BigDecimal discount = coupon.map(CouponPreview::discount).orElse(BigDecimal.ZERO);
        return CartDto.builder()
                .username(username)
                .items(cartItems.stream().map(this::convertToCartItemDto).toList())
                .totalPrice(subtotal)
                .totalItems(cartItems.stream().mapToInt(CartItemEntity::getQuantity).sum())
                .couponCode(coupon.map(CouponPreview::code).orElse(null))
                .couponValid(coupon.map(CouponPreview::valid).orElse(null))
                .couponMessage(coupon.map(CouponPreview::message).orElse(null))
                .discountAmount(discount)
                .totalAfterDiscount(subtotal.subtract(discount))
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
        couponService.removeFromCart(username);
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
