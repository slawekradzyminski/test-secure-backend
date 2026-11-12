package com.awesome.testing.dto.cart;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDto {

    @Schema(description = "Product id", example = "13")
    @NotNull
    private Long productId;

    @Schema(description = "Product quantity", example = "1")
    @NotNull
    @Min(1)
    private Integer quantity;

    /**
     * Price-change notice of a cart line, present only when the product price
     * changed after the line was last added or updated. The cart keeps, and
     * checkout charges, the unit price of that moment; adding to or updating
     * the line takes the current price.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(types = {"boolean", "null"}, accessMode = Schema.AccessMode.READ_ONLY,
            description = "Present (true) only when the product price changed after the line was last added or updated",
            example = "true")
    private Boolean priceChanged;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(types = {"number", "null"}, accessMode = Schema.AccessMode.READ_ONLY,
            description = "Unit price the cart keeps and checkout charges; present only when the price changed",
            example = "10.00")
    private BigDecimal unitPrice;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(types = {"number", "null"}, accessMode = Schema.AccessMode.READ_ONLY,
            description = "Current product price, which adding to or updating the line takes; present only when the price changed",
            example = "12.50")
    private BigDecimal currentPrice;

    public CartItemDto(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }
}