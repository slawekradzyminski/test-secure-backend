package com.awesome.testing.graphql;

import com.awesome.testing.dto.inventory.InventoryAdjustmentDto;
import com.awesome.testing.dto.inventory.InventoryMovementDto;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.product.ProductCreateDto;
import com.awesome.testing.dto.product.ProductUpdateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

import com.awesome.testing.graphql.CommerceViews.Cart;
import com.awesome.testing.graphql.CommerceViews.Order;
import com.awesome.testing.graphql.CommerceViews.Product;

@Controller
@Profile("graphql")
@RequiredArgsConstructor
public class CommerceMutationController {
    private final CommerceMutationService service;

    @MutationMapping
    public Cart addCartItem(@Argument String username, @Argument Long productId, @Argument int quantity) {
        return service.addCartItem(username, productId, quantity);
    }

    @MutationMapping
    public Cart updateCartItem(@Argument String username, @Argument Long productId, @Argument int quantity) {
        return service.updateCartItem(username, productId, quantity);
    }

    @MutationMapping
    public Cart removeCartItem(@Argument String username, @Argument Long productId) {
        return service.removeCartItem(username, productId);
    }

    @MutationMapping
    public Cart clearCart(@Argument String username) {
        return service.clearCart(username);
    }

    @MutationMapping
    public Order checkout(@Argument AddressDto address) {
        return service.checkout(address);
    }

    @MutationMapping
    public Order cancelOrder(@Argument Long id) {
        return service.cancelOrder(id);
    }

    @MutationMapping
    public Order updateOrderStatus(@Argument Long id, @Argument OrderStatus status) {
        return service.updateOrderStatus(id, status);
    }

    @MutationMapping
    public Product createProduct(@Argument ProductCreateDto input) {
        return service.createProduct(input);
    }

    @MutationMapping
    public Product updateProduct(@Argument Long id, @Argument ProductUpdateDto input) {
        return service.updateProduct(id, input);
    }

    @MutationMapping
    public boolean deleteProduct(@Argument Long id) {
        return service.deleteProduct(id);
    }

    @MutationMapping
    public InventoryMovementDto adjustInventory(@Argument Long productId, @Argument InventoryAdjustmentDto input) {
        return service.adjustInventory(productId, input);
    }
}
