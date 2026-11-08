package com.awesome.testing.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Rating summary over approved reviews")
public class ReviewSummaryDto {
    @Schema(description = "Product ID", example = "3")
    private Long productId;
    @Schema(types = {"number", "null"}, description = "Average rating rounded half-up to one decimal; null without reviews", example = "4.3")
    private BigDecimal averageRating;
    @Schema(description = "Number of approved reviews", example = "12")
    private long reviewCount;
    @Schema(description = "Approved review count for each star rating 1-5")
    private Map<Integer, Long> distribution;
}
