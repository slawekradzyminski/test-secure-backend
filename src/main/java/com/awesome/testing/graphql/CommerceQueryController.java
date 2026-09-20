package com.awesome.testing.graphql;

import com.awesome.testing.dto.inventory.InventoryItemDto;
import com.awesome.testing.dto.inventory.InventoryMovementDto;
import com.awesome.testing.dto.inventory.StockStatus;
import com.awesome.testing.dto.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import com.awesome.testing.graphql.CommerceViews.Cart;
import com.awesome.testing.graphql.CommerceViews.Order;
import com.awesome.testing.graphql.CommerceViews.Product;
import com.awesome.testing.graphql.CommerceViews.ProductPage;
import com.awesome.testing.graphql.CommerceViews.ResultPage;

@Controller
@Profile("graphql")
@RequiredArgsConstructor
public class CommerceQueryController {
    private final CommerceQueryService service;

    @QueryMapping
    public ProductPage products(@Argument int offset, @Argument int limit,
                                @Argument String category, @Argument Boolean inStockOnly) {
        return service.products(offset, limit, category, inStockOnly);
    }

    @QueryMapping
    public Product product(@Argument Long id) {
        return service.product(id);
    }

    @QueryMapping
    public Cart cart(@Argument String username) {
        return service.cart(username);
    }

    @QueryMapping
    public ResultPage<Cart> carts(@Argument int page, @Argument int size) {
        return service.carts(page, size);
    }

    @QueryMapping
    public Order order(@Argument Long id) {
        return service.order(id);
    }

    @QueryMapping
    public ResultPage<Order> orders(@Argument String username, @Argument OrderStatus status,
                                    @Argument int page, @Argument int size) {
        return service.orders(username, status, page, size);
    }

    @QueryMapping
    public ResultPage<InventoryItemDto> inventory(@Argument int page, @Argument int size,
                                                 @Argument String search, @Argument String category,
                                                 @Argument StockStatus status, @Argument int lowStockThreshold) {
        return service.inventory(page, size, search, category, status, lowStockThreshold);
    }

    @QueryMapping
    public InventoryItemDto inventoryItem(@Argument Long productId, @Argument int lowStockThreshold) {
        return service.inventoryItem(productId, lowStockThreshold);
    }

    @QueryMapping
    public ResultPage<InventoryMovementDto> inventoryMovements(@Argument Long productId,
                                                              @Argument int page, @Argument int size) {
        return service.inventoryMovements(productId, page, size);
    }
}
