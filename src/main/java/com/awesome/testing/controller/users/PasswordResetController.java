package com.awesome.testing.controller.users;

import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.ErrorDto;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import com.awesome.testing.dto.password.ForgotPasswordRequestDto;
import com.awesome.testing.dto.password.ForgotPasswordResponseDto;
import com.awesome.testing.dto.password.ResetPasswordRequestDto;
import com.awesome.testing.security.ratelimit.AuthRateLimitGuard;
import com.awesome.testing.service.password.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/password")
@RequiredArgsConstructor
@ApiResponse(responseCode = "401", description = "Invalid or expired Bearer token; omit stale Authorization headers on these public endpoints",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
@Tag(name = "password-reset", description = "Password recovery endpoints")
public class PasswordResetController {

    private final AuthRateLimitGuard authRateLimitGuard;
    private final PasswordResetService passwordResetService;

    @PostMapping("/forgot")
    @Operation(summary = "Start password reset flow",
            description = "Accepts a username or email and queues reset instructions when a matching local account exists.")
    @ApiResponse(responseCode = "202", description = "Request accepted whether or not a matching account exists")
    @ApiResponse(responseCode = "400", description = "Invalid payload",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "429", description = "Too many requests",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<ForgotPasswordResponseDto> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDto request,
            HttpServletRequest servletRequest) {
        authRateLimitGuard.checkForgotPassword(servletRequest, request.getIdentifier());
        ForgotPasswordResponseDto response = passwordResetService.requestReset(
                request.getIdentifier(),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"));
        return ResponseEntity.accepted().body(response);
    }

    @PostMapping("/reset")
    @Operation(summary = "Complete password reset with a valid token",
            description = "Validates a password reset token, updates the user's password, and revokes existing refresh tokens.")
    @ApiResponse(responseCode = "200", description = "Password reset successful", content = @Content)
    @ApiResponse(responseCode = "400", description = "Invalid token or payload",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "429", description = "Too many requests",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request,
                                              HttpServletRequest servletRequest) {
        authRateLimitGuard.checkResetPassword(servletRequest);
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok().build();
    }
}
