package com.awesome.testing.graphql;

import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.cart.UpdateCartItemDto;
import com.awesome.testing.dto.inventory.InventoryAdjustmentDto;
import com.awesome.testing.dto.inventory.InventoryMovementDto;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.product.ProductCreateDto;
import com.awesome.testing.dto.product.ProductUpdateDto;
import com.awesome.testing.service.CartService;
import com.awesome.testing.service.InventoryService;
import com.awesome.testing.service.OrderService;
import com.awesome.testing.service.ProductService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import com.awesome.testing.graphql.CommerceViews.Cart;
import com.awesome.testing.graphql.CommerceViews.Order;
import com.awesome.testing.graphql.CommerceViews.Product;

@Service
@Profile("graphql")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Transactional(timeout = 5)
public class CommerceMutationService {
    private final CommerceAccess access;
    private final CommerceQueryService queries;
    private final CartService cartService;
    private final OrderService orderService;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final Validator validator;

    public Cart addCartItem(String username, Long productId, int quantity) {
        String owner = access.owner(username);
        cartService.addToCart(owner, validated(new CartItemDto(productId, quantity)));
        return queries.cart(owner);
    }

    public Cart updateCartItem(String username, Long productId, int quantity) {
        String owner = access.owner(username);
        cartService.updateCartItem(owner, productId, validated(new UpdateCartItemDto(quantity)));
        return queries.cart(owner);
    }

    public Cart removeCartItem(String username, Long productId) {
        String owner = access.owner(username);
        cartService.removeFromCart(owner, productId);
        return queries.cart(owner);
    }

    public Cart clearCart(String username) {
        String owner = access.owner(username);
        cartService.clearCart(owner);
        return queries.cart(owner);
    }

    public Order checkout(AddressDto address) {
        return Order.from(orderService.createOrder(access.username(), validated(address)));
    }

    public Order cancelOrder(Long id) {
        return Order.from(orderService.cancelOrder(id, access.username(), access.isAdmin()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Order updateOrderStatus(Long id, OrderStatus status) {
        return Order.from(orderService.updateOrderStatus(id, status));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Product createProduct(ProductCreateDto input) {
        return Product.from(productService.createProduct(validated(input)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Product updateProduct(Long id, ProductUpdateDto input) {
        return Product.from(productService.updateProduct(id, validated(input)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public boolean deleteProduct(Long id) {
        return productService.deleteProduct(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public InventoryMovementDto adjustInventory(Long productId, InventoryAdjustmentDto input) {
        return inventoryService.adjust(productId, validated(input), access.username());
    }

    private <T> T validated(T input) {
        Set<ConstraintViolation<T>> violations = validator.validate(input);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return input;
    }
}
