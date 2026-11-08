package com.awesome.testing.dto.returns;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Rejection reason shown to the customer")
public class RejectReturnDto {
    @NotBlank(message = "A note is required when rejecting a return")
    @Size(max = 500, message = "Note cannot exceed 500 characters")
    @Schema(minLength = 1, pattern = "\\S", maxLength = 500, description = "Reason", example = "Item shows signs of use")
    private String note;
}
