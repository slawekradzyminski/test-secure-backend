package com.awesome.testing.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Whether the caller may review a product, and their existing review")
public class MyReviewDto {
    @Schema(description = "True when the caller may create a review now", example = "true")
    private boolean canReview;
    @Schema(types = {"string", "null"}, description = "Why the caller cannot create a review",
            example = "Only customers who received this product can review it")
    private String reason;
    @Schema(description = "The caller's review, if any")
    private ReviewDto review;
}
