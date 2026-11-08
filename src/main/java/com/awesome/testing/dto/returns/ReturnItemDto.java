package com.awesome.testing.dto.returns;

import com.awesome.testing.entity.returns.ItemCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Returned item")
public class ReturnItemDto {
    @Schema(description = "Product ID", example = "3")
    private Long productId;
    @Schema(description = "Product name", example = "iPhone 13 Pro")
    private String productName;
    @Schema(description = "Units returned", example = "1")
    private Integer quantity;
    @Schema(description = "Unit price paid before discounts", example = "999.99")
    private BigDecimal unitPrice;
    @Schema(types = {"string", "null"}, description = "Condition recorded on receipt", example = "RESELLABLE")
    private ItemCondition condition;
}
