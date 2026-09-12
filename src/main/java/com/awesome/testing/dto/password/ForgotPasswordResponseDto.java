package com.awesome.testing.dto.password;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ForgotPasswordResponseDto {

    @Schema(description = "Human readable status message")
    String message;

    @Schema(types = {"string", "null"}, description = "Raw password reset token only when password-reset.expose-token-in-response is enabled and a local account matches; otherwise null")
    String token;
}
