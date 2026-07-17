package com.awesome.testing.dto.password;

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
public class ForgotPasswordRequestDto {

    @Schema(description = "Username or email address associated with the account",
            example = "client")
    @NotBlank(message = "Identifier is required")
    private String identifier;
}
