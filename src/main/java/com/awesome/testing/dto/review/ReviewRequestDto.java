package com.awesome.testing.dto.review;

import com.awesome.testing.dto.validation.MinLengthUnlessBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Product review content")
public class ReviewRequestDto {

    private static final String TITLE_LENGTH = "Title must be between 3 and 120 characters";
    private static final String BODY_LENGTH = "Review text must be between 10 and 2000 characters";

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    @Schema(minimum = "1", maximum = "5", description = "Star rating", example = "5")
    private Integer rating;

    @NotBlank(message = "Title is required")
    @Size(max = 120, message = TITLE_LENGTH)
    @MinLengthUnlessBlank(value = 3, message = TITLE_LENGTH)
    @Schema(minLength = 1, pattern = "\\S", maxLength = 120, description = "Review title", example = "Great phone")
    private String title;

    @NotBlank(message = "Review text is required")
    @Size(max = 2000, message = BODY_LENGTH)
    @MinLengthUnlessBlank(value = 10, message = BODY_LENGTH)
    @Schema(minLength = 1, pattern = "\\S", maxLength = 2000, description = "Review text", example = "Battery lasts two days and the camera is excellent.")
    private String body;
}
