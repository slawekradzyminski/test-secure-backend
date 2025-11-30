package com.awesome.testing.dto.password;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ForgotPasswordResponseDto {

    @Schema(description = "Human readable status message")
    String message;

    @Schema(description = "Raw password reset token, returned only for local/testing profiles")
    String token;
}
