package com.awesome.testing.dto.password;

import com.awesome.testing.dto.validation.MaxUtf8Bytes;
import com.awesome.testing.dto.validation.MinLengthUnlessBlank;
import com.awesome.testing.security.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequestDto {

    @Schema(minLength = 1, pattern = "\\S", description = "Password reset token received via email")
    @NotBlank(message = "Token is required")
    private String token;

    @Schema(minLength = 8, maxLength = 72, pattern = "\\S",
            description = "New password of at least 8 characters and at most 72 bytes in UTF-8 (the BCrypt limit)",
            example = "password123")
    @NotBlank(message = "Password is required")
    // @MinLengthUnlessBlank instead of @Size(min): a @Size would publish maxLength = Integer.MAX_VALUE
    @MinLengthUnlessBlank(value = 8, message = "Minimum password length: 8 characters")
    @MaxUtf8Bytes(value = PasswordPolicy.MAX_BYTES, message = PasswordPolicy.MAX_BYTES_MESSAGE)
    private String newPassword;

    @Schema(minLength = 1, pattern = "\\S", description = "Confirmation of the new password", example = "password123")
    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;
}
