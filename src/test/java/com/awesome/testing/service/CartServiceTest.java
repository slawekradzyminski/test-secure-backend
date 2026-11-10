package com.awesome.testing.service;

import com.awesome.testing.service.checkout.CartSummaryContext;
import com.awesome.testing.service.checkout.CartSummaryContributors;
import com.awesome.testing.service.coupon.CouponPreview;
import com.awesome.testing.service.coupon.CouponService;
import com.awesome.testing.service.tender.CartTenderService;
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
import java.util.ArrayList;
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

    @Mock
    private InventoryService inventoryService;

    @Mock
    private CouponService couponService;

    @Mock
    private CartSummaryContributors cartSummaryContributors;

    @Mock
    private CartTenderService cartTenderService;

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
        assertThat(cart.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductId()).isEqualTo(product.getId());
            assertThat(item.getQuantity()).isEqualTo(1);
        });
    }

    @Test
    void cartSummaryContributorsSeeTheCartAndMayAddFields() {
        when(cartItemRepository.findByUsername(USERNAME)).thenReturn(List.of(cartItem));
        when(couponService.preview(USERNAME, product.getPrice())).thenReturn(Optional.of(
                new CouponPreview("SAVE10", true, null, new BigDecimal("100.00"))));
        List<CartSummaryContext> seen = new ArrayList<>();
        CartService withContributor = new CartService(cartItemRepository, productRepository, inventoryService,
                couponService, new CartSummaryContributors(List.of(context -> {
                    seen.add(context);
                    context.cart().setCouponMessage("contributed");
                })), cartTenderService);

        CartDto cart = withContributor.getCart(USERNAME);

        assertThat(seen).singleElement().satisfies(context -> {
            assertThat(context.username()).isEqualTo(USERNAME);
            assertThat(context.cartItems()).containsExactly(cartItem);
            assertThat(context.subtotal()).isEqualByComparingTo("1000");
            assertThat(context.discount()).isEqualByComparingTo("100.00");
            assertThat(context.cart()).isSameAs(cart);
        });
        assertThat(cart.getCouponMessage()).isEqualTo("contributed");
        assertThat(cart.getDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(cart.getTotalAfterDiscount()).isEqualByComparingTo("900.00");
    }

    @Test
    void cartSummaryContributorsGetAZeroDiscountWithoutACoupon() {
        when(cartItemRepository.findByUsername(USERNAME)).thenReturn(List.of());
        List<CartSummaryContext> seen = new ArrayList<>();
        CartService withContributor = new CartService(cartItemRepository, productRepository, inventoryService,
                couponService, new CartSummaryContributors(List.of(seen::add)), cartTenderService);

        CartDto cart = withContributor.getCart(USERNAME);

        assertThat(cart.getDiscountAmount()).isNull();
        assertThat(cart.getTotalAfterDiscount()).isNull();
        assertThat(seen).singleElement().satisfies(context -> {
            assertThat(context.discount()).isEqualByComparingTo("0");
            assertThat(context.subtotal()).isEqualByComparingTo("0");
        });
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
        verify(inventoryService).checkAvailable(product, 2);
        verify(cartItemRepository).save(any(CartItemEntity.class));
    }

    @Test
    void shouldIncreaseQuantityWhenItemAlreadyExists() {
        product.setPrice(BigDecimal.valueOf(1250));
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
        assertThat(cartItem.getPrice()).isEqualByComparingTo("1250");
        verify(cartItemRepository).save(cartItem);
        assertThat(cart.getTotalItems()).isEqualTo(4);
        assertThat(cart.getTotalPrice()).isEqualByComparingTo("5000");
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

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void shouldUpdateCartItemQuantity() {
        product.setPrice(BigDecimal.valueOf(1250));
        UpdateCartItemDto dto = UpdateCartItemDto.builder().quantity(5).build();
        when(cartItemRepository.findByUsernameAndProductId(USERNAME, product.getId()))
                .thenReturn(Optional.of(cartItem));
        when(cartItemRepository.findByUsername(USERNAME)).thenReturn(List.of(cartItem));

        CartDto cart = cartService.updateCartItem(USERNAME, product.getId(), dto);

        assertThat(cartItem.getQuantity()).isEqualTo(5);
        assertThat(cartItem.getPrice()).isEqualByComparingTo("1250");
        assertThat(cart.getTotalItems()).isEqualTo(5);
        assertThat(cart.getTotalPrice()).isEqualByComparingTo("6250");
        verify(inventoryService).checkAvailable(product, 5);
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

        verify(cartItemRepository, never()).save(any());
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

    @Test
    void clearingTheCartAlsoDropsTheAppliedCoupon() {
        cartService.clearCart(USERNAME);

        verify(cartItemRepository).deleteByUsername(USERNAME);
        verify(couponService).removeFromCart(USERNAME);
    }

    @Test
    void clearingTheCartAlsoDropsTheAppliedGiftCardAndPoints() {
        cartService.clearCart(USERNAME);

        verify(cartTenderService).clear(USERNAME);
    }
}
