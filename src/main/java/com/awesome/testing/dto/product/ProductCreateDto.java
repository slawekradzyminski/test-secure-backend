package com.awesome.testing.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create product data transfer object")
public class ProductCreateDto {

    @NotBlank(message = "Product name is required")
    @Size(min = 3, max = 100, message = "Product name must be between 3 and 100 characters")
    @Schema(minLength = 1, pattern = "\\S", description = "Product name", example = "iPhone 13")
    private String name;

    @NotBlank(message = "Product description is required")
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Schema(minLength = 1, pattern = "\\S", description = "Nonblank product description required on creation; updates may use an empty description", example = "Latest iPhone model with A15 Bionic chip")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 digits and 2 decimals")
    @Schema(multipleOf = 0.01, maximum = "99999999.99", description = "Product price with at most 8 integer digits and 2 fractional digits", example = "999.99")
    private BigDecimal price;

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    @Schema(description = "Available stock quantity", example = "100")
    private Integer stockQuantity;

    @NotBlank(message = "Category is required")
    @Schema(minLength = 1, pattern = "\\S", description = "Product category", example = "Electronics")
    private String category;

    @Pattern(regexp = "^(https?://.*|)$", message = "Image URL must be a valid URL or empty")
    @Schema(types = {"string", "null"}, description = "Product image URL", example = "https://example.com/iphone13.jpg")
    private String imageUrl;

}
