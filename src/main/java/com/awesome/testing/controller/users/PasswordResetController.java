package com.awesome.testing.controller.users;

import com.awesome.testing.dto.password.ForgotPasswordRequestDto;
import com.awesome.testing.dto.password.ForgotPasswordResponseDto;
import com.awesome.testing.dto.password.ResetPasswordRequestDto;
import com.awesome.testing.service.password.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/users/password")
@RequiredArgsConstructor
@Tag(name = "password-reset", description = "Password recovery endpoints")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/forgot")
    @Operation(summary = "Start password reset flow")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Reset email queued"),
            @ApiResponse(responseCode = "400", description = "Invalid payload", content = @Content)
    })
    public ResponseEntity<ForgotPasswordResponseDto> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDto request,
            HttpServletRequest servletRequest) {
        ForgotPasswordResponseDto response = passwordResetService.requestReset(
                request.getIdentifier(),
                request.getResetBaseUrl(),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent"));
        return ResponseEntity.accepted().body(response);
    }

    @PostMapping("/reset")
    @Operation(summary = "Complete password reset with a valid token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successful"),
            @ApiResponse(responseCode = "400", description = "Invalid token or payload", content = @Content)
    })
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok().build();
    }
}
