package com.awesome.testing.controller.users;

import com.awesome.testing.dto.user.RefreshTokenRequestDto;
import com.awesome.testing.dto.user.TokenPair;
import com.awesome.testing.dto.user.TokenRefreshResponseDto;
import com.awesome.testing.security.ratelimit.AuthRateLimitGuard;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
public class UserRefreshController {

    private final AuthRateLimitGuard authRateLimitGuard;
    private final UserService userService;

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT token using refresh token",
            description = "Rotates a valid refresh token and returns a fresh access token plus replacement refresh token.")
    @ApiResponse(responseCode = "200", description = "New JWT and refresh tokens")
    @ApiResponse(responseCode = "400", description = "Bad request",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsDto.class)))
    @ApiResponse(responseCode = "401", description = "Invalid refresh token",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "429", description = "Too many requests",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public TokenRefreshResponseDto refresh(HttpServletRequest servletRequest,
                                           @Valid @RequestBody RefreshTokenRequestDto request) {
        authRateLimitGuard.checkRefresh(servletRequest);
        TokenPair tokens = userService.refresh(request.getRefreshToken());
        return TokenRefreshResponseDto.from(tokens);
    }

}
