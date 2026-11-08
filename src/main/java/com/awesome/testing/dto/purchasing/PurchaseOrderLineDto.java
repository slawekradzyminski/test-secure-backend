package com.awesome.testing.dto.purchasing;

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
@Schema(description = "Purchase order line")
public class PurchaseOrderLineDto {
    @Schema(description = "Product ID", example = "3")
    private Long productId;
    @Schema(description = "Product name", example = "iPhone 13 Pro")
    private String productName;
    @Schema(description = "Units ordered", example = "50")
    private Integer orderedQuantity;
    @Schema(description = "Units received so far", example = "20")
    private Integer receivedQuantity;
    @Schema(description = "Units still expected", example = "30")
    private Integer outstandingQuantity;
    @Schema(description = "Purchase price per unit", example = "640.00")
    private BigDecimal unitCost;
    @Schema(description = "orderedQuantity × unitCost", example = "32000.00")
    private BigDecimal lineTotal;
}
