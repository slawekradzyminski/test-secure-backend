package com.awesome.testing.graphql;

import com.awesome.testing.dto.inventory.InventoryItemDto;
import com.awesome.testing.dto.inventory.InventoryMovementDto;
import com.awesome.testing.dto.inventory.StockStatus;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.product.ProductDto;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.graphql.CommerceViews.Cart;
import com.awesome.testing.graphql.CommerceViews.Order;
import com.awesome.testing.graphql.CommerceViews.Product;
import com.awesome.testing.graphql.CommerceViews.ProductPage;
import com.awesome.testing.graphql.CommerceViews.ResultPage;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.ProductSpecifications;
import com.awesome.testing.repository.pagination.OffsetPageRequest;
import com.awesome.testing.service.InventoryService;
import com.awesome.testing.service.OrderService;
import com.awesome.testing.service.ProductService;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Secured application queries shared by the GraphQL resolvers. */
@Service
@Profile("graphql")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Transactional(readOnly = true, timeout = 5)
public class CommerceQueryService {
    private final CommerceAccess access;
    private final ProductService productService;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final CommerceOrderQueryService orderQueries;

    public ProductPage products(int offset, int limit, String category, Boolean inStockOnly, PageSelection selection) {
        validatePage(offset, limit);
        Specification<ProductEntity> filter = ProductSpecifications.catalog(category, inStockOnly);
        if (!selection.items()) {
            return new ProductPage(
                    List.of(), selection.total() ? Math.toIntExact(productRepository.count(filter)) : 0, offset, limit);
        }
        OffsetPageRequest pagination = new OffsetPageRequest(offset, limit);
        Slice<ProductEntity> rows =
                selection.total()
                        ? productRepository.findAll(filter, pagination)
                        : productRepository.findBy(filter, query -> query.slice(pagination));
        int total = rows instanceof Page<?> page ? Math.toIntExact(page.getTotalElements()) : 0;
        return new ProductPage(
                rows.getContent().stream().map(ProductDto::from).map(Product::from).toList(), total, offset, limit);
    }

    public Product product(Long id) {
        return Product.from(productService.getProductById(id));
    }

    public Cart cart(String username) {
        String owner = access.owner(username);
        return Cart.from(owner, cartItemRepository.findByUsername(owner));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public ResultPage<Cart> carts(int page, int size) {
        validatePage(page, size);
        Page<String> owners = cartItemRepository.findCartOwners(PageRequest.of(page, size));
        Map<String, List<CartItemEntity>> rows =
                cartItemRepository.findByUsernames(owners.getContent()).stream()
                        .collect(Collectors.groupingBy(CartItemEntity::getUsername));
        return ResultPage.from(owners, owner -> Cart.from(owner, rows.getOrDefault(owner, List.of())));
    }

    public ResultPage<Order> orders(String username, OrderStatus status, int page, int size, PageSelection selection) {
        validatePage(page, size);
        return orderQueries.orders(username, status, page, size, selection);
    }

    public Order order(Long id) {
        return Order.from(
                access.isAdmin() ? orderService.getOrderById(id) : orderService.getOrder(access.username(), id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public ResultPage<InventoryItemDto> inventory(
            int page, int size, String search, String category, StockStatus status, int lowStockThreshold) {
        validatePage(page, size);
        return ResultPage.from(
                inventoryService.list(page, size, search, category, status, lowStockThreshold), Function.identity());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public InventoryItemDto inventoryItem(Long productId, int lowStockThreshold) {
        return inventoryService.get(productId, lowStockThreshold);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public ResultPage<InventoryMovementDto> inventoryMovements(Long productId, int page, int size) {
        return ResultPage.from(inventoryService.movements(productId, pageRequest(page, size)), Function.identity());
    }

    private static PageRequest pageRequest(int page, int size) {
        validatePage(page, size);
        return PageRequest.of(page, size, Sort.by("id").ascending());
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page/offset must be non-negative and size/limit between 1 and 100");
        }
    }
}
