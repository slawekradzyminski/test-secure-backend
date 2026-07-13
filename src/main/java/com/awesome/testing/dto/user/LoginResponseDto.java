package com.awesome.testing.dto.user;

import com.awesome.testing.entity.UserEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {

    @Schema(description = "JWT token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    String token;

    @Schema(description = "Refresh token")
    String refreshToken;

    @Schema(description = "Username", example = "johndoe")
    String username;

    @Schema(description = "Email address", example = "john.doe@example.com")
    String email;

    @Schema(description = "First name", example = "John")
    String firstName;

    @Schema(description = "Last name", example = "Doe")
    String lastName;

    @Schema(description = "User roles", example = "[\"ROLE_CLIENT\"]")
    List<Role> roles;

    @Schema(description = "Whether a second factor is required before tokens can be issued")
    boolean mfaRequired;

    @Schema(description = "Short-lived, single-use MFA challenge token")
    String challengeToken;

    @Schema(description = "MFA challenge expiration time")
    Instant challengeExpiresAt;

    public static LoginResponseDto from(TokenPair tokenPair, UserEntity user) {
        return LoginResponseDto.builder()
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles())
                .token(tokenPair.getToken())
                .refreshToken(tokenPair.getRefreshToken())
                .email(user.getEmail())
                .build();
    }

    public static LoginResponseDto mfaChallenge(UserEntity user, String challengeToken, Instant expiresAt) {
        return LoginResponseDto.builder()
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles())
                .email(user.getEmail())
                .mfaRequired(true)
                .challengeToken(challengeToken)
                .challengeExpiresAt(expiresAt)
                .build();
    }

}
