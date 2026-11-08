package com.awesome.testing.dto.review;

import com.awesome.testing.entity.review.ReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Product review")
public class ReviewDto {
    @Schema(description = "Review ID", example = "1")
    private Long id;
    @Schema(description = "Product ID", example = "3")
    private Long productId;
    @Schema(description = "Product name", example = "iPhone 13 Pro")
    private String productName;
    @Schema(description = "Author username", example = "client")
    private String username;
    @Schema(description = "Star rating", example = "5")
    private Integer rating;
    @Schema(description = "Review title", example = "Great phone")
    private String title;
    @Schema(description = "Review text")
    private String body;
    @Schema(description = "Moderation status", example = "APPROVED")
    private ReviewStatus status;
    @Schema(types = {"string", "null"}, description = "Moderator note, visible to the author and administrators")
    private String moderationNote;
    @Schema(type = "string", format = "local-date-time", description = "Creation timestamp")
    private LocalDateTime createdAt;
    @Schema(type = "string", format = "local-date-time", description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
