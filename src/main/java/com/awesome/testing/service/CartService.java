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
import com.awesome.testing.service.checkout.CartSummaryContext;
import com.awesome.testing.service.checkout.CartSummaryContributors;
import com.awesome.testing.service.coupon.CouponPreview;
import com.awesome.testing.service.coupon.CouponService;
import com.awesome.testing.service.tender.CartTenderService;
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
    private final CartSummaryContributors cartSummaryContributors;
    private final CartTenderService cartTenderService;

    @Transactional(readOnly = true)
    public CartDto getCart(String username) {
        List<CartItemEntity> cartItems = cartItemRepository.findByUsername(username);
        BigDecimal subtotal = calculateTotalPrice(cartItems);
        Optional<CouponPreview> coupon = couponService.preview(username, subtotal);
        BigDecimal discount = coupon.map(CouponPreview::discount).orElse(BigDecimal.ZERO);
        CartDto cart = CartDto.builder()
                .username(username)
                .items(cartItems.stream().map(this::convertToCartItemDto).toList())
                .totalPrice(subtotal)
                .totalItems(cartItems.stream().mapToInt(CartItemEntity::getQuantity).sum())
                .couponCode(coupon.map(CouponPreview::code).orElse(null))
                .couponValid(coupon.map(CouponPreview::valid).orElse(null))
                .couponMessage(coupon.map(CouponPreview::message).orElse(null))
                // Without a coupon the cart serializes exactly as before coupons existed.
                .discountAmount(coupon.isPresent() ? discount : null)
                .totalAfterDiscount(coupon.isPresent() ? subtotal.subtract(discount) : null)
                .build();
        cartSummaryContributors.contribute(new CartSummaryContext(username, cartItems, subtotal, discount, cart));
        return cart;
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
        cartTenderService.clear(username);
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

    /**
     * The cart keeps the unit price of the moment a line was last added or
     * updated, and checkout charges it. A line whose product price changed since
     * then also carries that unit price and the current price, so the client can
     * tell the customer; unchanged lines serialize exactly as before.
     */
    private CartItemDto convertToCartItemDto(CartItemEntity item) {
        CartItemDto.CartItemDtoBuilder line = CartItemDto.builder()
                .productId(item.getProduct().getId())
                .quantity(item.getQuantity());
        BigDecimal currentPrice = item.getProduct().getPrice();
        if (item.getPrice().compareTo(currentPrice) != 0) {
            line.priceChanged(true)
                    .unitPrice(item.getPrice())
                    .currentPrice(currentPrice);
        }
        return line.build();
    }

    private BigDecimal calculateTotalPrice(List<CartItemEntity> cartItems) {
        return cartItems.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

} 
