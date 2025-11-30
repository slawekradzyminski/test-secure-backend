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

    @Schema(description = "Optional override for the frontend reset page base URL. "
            + "Must include scheme (http/https). When not provided the backend default is used.",
            example = "http://localhost:8081/reset")
    private String resetBaseUrl;
}
