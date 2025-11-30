package com.awesome.testing.dto.password;

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
public class ResetPasswordRequestDto {

    @Schema(description = "Password reset token received via email")
    @NotBlank(message = "Token is required")
    private String token;

    @Schema(description = "New password", example = "password123")
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Minimum password length: 8 characters")
    private String newPassword;

    @Schema(description = "Confirmation of the new password", example = "password123")
    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;
}
