package com.awesome.testing.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TokenRefreshResponseDto {

    @Schema(description = "Fresh JWT access token")
    String token;

    @Schema(description = "Rotated refresh token")
    String refreshToken;

    public static TokenRefreshResponseDto from(TokenPair tokenPair) {
        return TokenRefreshResponseDto.builder()
                .token(tokenPair.getToken())
                .refreshToken(tokenPair.getRefreshToken())
                .build();
    }
}
