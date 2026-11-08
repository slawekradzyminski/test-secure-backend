package com.awesome.testing.dto.purchasing;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
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
@Schema(description = "Supplier")
public class SupplierRequestDto {
    @NotBlank(message = "Supplier name is required")
    @Size(min = 2, max = 120, message = "Supplier name must be between 2 and 120 characters")
    @Schema(minLength = 1, pattern = "\\S", maxLength = 120, description = "Unique name, case-insensitive", example = "Nordic Electronics")
    private String name;

    @NotBlank(message = "Supplier email is required")
    @Email(message = "Invalid email format")
    @Schema(minLength = 1, pattern = "\\S", description = "Purchasing contact", example = "orders@nordic.example")
    private String email;

    @NotNull(message = "Lead time is required")
    @Min(value = 0, message = "Lead time must be between 0 and 365 days")
    @Max(value = 365, message = "Lead time must be between 0 and 365 days")
    @Schema(minimum = "0", maximum = "365", description = "Days between submission and expected delivery", example = "7")
    private Integer leadTimeDays;
}
