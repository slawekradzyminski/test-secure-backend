package com.awesome.testing.dto.product;

import com.awesome.testing.entity.ProductEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private String category;
    @Schema(types = {"string", "null"})
    private String imageUrl;
    @Schema(type = "string", format = "local-date-time", description = "Server local date and time without an offset. The server timezone is deployment-dependent; this value does not identify an absolute instant.", example = "2026-09-10T10:30:02.809462")
    private LocalDateTime createdAt;
    @Schema(type = "string", format = "local-date-time", description = "Server local date and time without an offset. The server timezone is deployment-dependent; this value does not identify an absolute instant.", example = "2026-09-10T10:30:02.809462")
    private LocalDateTime updatedAt;

    public static ProductDto from(ProductEntity productEntity) {
        return ProductDto.builder()
                .id(productEntity.getId())
                .name(productEntity.getName())
                .description(productEntity.getDescription())
                .price(productEntity.getPrice())
                .stockQuantity(productEntity.getStockQuantity())
                .category(productEntity.getCategory())
                .imageUrl(productEntity.getImageUrl())
                .createdAt(productEntity.getCreatedAt())
                .updatedAt(productEntity.getUpdatedAt())
                .build();
    }

} 