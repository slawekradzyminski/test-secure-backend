package com.awesome.testing.entity;

import com.awesome.testing.dto.product.ProductCreateDto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Check(name = "ck_products_stock_nonnegative", constraints = "stock_quantity >= 0")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private String category;

    private String imageUrl;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static ProductEntity from(ProductCreateDto productCreateDto) {
        return ProductEntity.builder()
                .name(productCreateDto.getName())
                .description(productCreateDto.getDescription())
                .price(productCreateDto.getPrice())
                .stockQuantity(productCreateDto.getStockQuantity())
                .category(productCreateDto.getCategory())
                .imageUrl(productCreateDto.getImageUrl())
                .build();
    }
} 
