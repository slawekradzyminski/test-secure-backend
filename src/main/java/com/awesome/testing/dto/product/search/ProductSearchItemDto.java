package com.awesome.testing.dto.product.search;

import com.awesome.testing.dto.product.ProductDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One search hit: the {@link ProductDto} fields plus the approved-review rating. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Product search hit: the product fields plus its approved-review rating")
public class ProductSearchItemDto {

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
    @Schema(types = {"number", "null"}, description = "Average of approved review ratings rounded half-up to one decimal; null without approved reviews", example = "4.3")
    private BigDecimal averageRating;
    @Schema(description = "Number of approved reviews", example = "12")
    private long reviewCount;

    public static ProductSearchItemDto from(ProductDto product, BigDecimal averageRating, long reviewCount) {
        return ProductSearchItemDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .category(product.getCategory())
                .imageUrl(product.getImageUrl())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .build();
    }
}
