package com.awesome.testing.dto.wishlist;

import com.awesome.testing.dto.inventory.StockStatus;
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
@Schema(description = "Wishlist entry with the product's current price and availability")
public class WishlistItemDto {
    @Schema(description = "Product ID", example = "3")
    private Long productId;
    @Schema(description = "Product name", example = "iPhone 13 Pro")
    private String productName;
    @Schema(description = "Current product price", example = "999.99")
    private BigDecimal price;
    @Schema(types = {"string", "null"}, description = "Product image URL")
    private String imageUrl;
    @Schema(description = "Current stock quantity", example = "0")
    private Integer stockQuantity;
    @Schema(description = "OUT_OF_STOCK when quantity is zero, otherwise IN_STOCK", example = "OUT_OF_STOCK")
    private StockStatus stockStatus;
    @Schema(description = "Email the user when the product is back in stock", example = "true")
    private Boolean notifyWhenAvailable;
    @Schema(types = {"string", "null"}, format = "local-date-time", description = "Last back-in-stock email")
    private LocalDateTime lastNotifiedAt;
    @Schema(type = "string", format = "local-date-time", description = "When the product was added")
    private LocalDateTime addedAt;
}
