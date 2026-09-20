package com.awesome.testing.graphql;

import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderItemDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.product.ProductDto;
import com.awesome.testing.entity.CartItemEntity;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/** Immutable API projections. Money never passes through floating-point conversion. */
public final class CommerceViews {
    private CommerceViews() { }

    public static String money(BigDecimal value) {
        return value.toPlainString();
    }

    public record Product(Long id, String name, String description, String price, Integer stockQuantity,
                          String category, String imageUrl) {
        public static Product from(ProductDto dto) {
            return new Product(dto.getId(), dto.getName(), dto.getDescription(), money(dto.getPrice()),
                    dto.getStockQuantity(), dto.getCategory(), dto.getImageUrl());
        }
    }

    public record ProductPage(List<Product> items, int total, int offset, int limit) { }

    public record Cart(String username, List<CartItem> items, String totalPrice, int totalItems) {
        public static Cart from(String username, List<CartItemEntity> rows) {
            List<CartItem> items = rows.stream().map(CartItem::from).toList();
            BigDecimal total = rows.stream()
                    .map(row -> row.getPrice().multiply(BigDecimal.valueOf(row.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new Cart(username, items, money(total),
                    rows.stream().mapToInt(CartItemEntity::getQuantity).sum());
        }
    }

    public record CartItem(Product product, int quantity, String unitPrice) {
        private static CartItem from(CartItemEntity row) {
            return new CartItem(Product.from(ProductDto.from(row.getProduct())),
                    row.getQuantity(), money(row.getPrice()));
        }
    }

    public record ResultPage<T>(List<T> items, int total, int page, int size) {
        public static <S, T> ResultPage<T> from(Page<S> source, Function<S, T> mapper) {
            return new ResultPage<>(source.getContent().stream().map(mapper).toList(),
                    Math.toIntExact(source.getTotalElements()), source.getNumber(), source.getSize());
        }
    }

    public record Order(Long id, String username, List<OrderItem> items, String totalAmount,
                        OrderStatus status, AddressDto shippingAddress, String createdAt, String updatedAt) {
        public static Order from(OrderDto dto) {
            return new Order(dto.getId(), dto.getUsername(), dto.getItems().stream().map(OrderItem::from).toList(),
                    money(dto.getTotalAmount()), dto.getStatus(), dto.getShippingAddress(),
                    dto.getCreatedAt().toString(), dto.getUpdatedAt().toString());
        }
    }

    public record OrderItem(Long id, Long productId, String productName, int quantity,
                            String unitPrice, String totalPrice) {
        private static OrderItem from(OrderItemDto dto) {
            return new OrderItem(dto.getId(), dto.getProductId(), dto.getProductName(), dto.getQuantity(),
                    money(dto.getUnitPrice()), money(dto.getTotalPrice()));
        }
    }
}
