package com.awesome.testing.controller.users;

import com.awesome.testing.dto.mfa.MfaChallengeRequestDto;
import com.awesome.testing.dto.mfa.MfaCodeRequestDto;
import com.awesome.testing.dto.mfa.MfaProtectedActionRequestDto;
import com.awesome.testing.dto.mfa.MfaRecoveryCodesResponseDto;
import com.awesome.testing.dto.mfa.MfaSetupResponseDto;
import com.awesome.testing.dto.mfa.MfaStatusResponseDto;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.security.ratelimit.AuthRateLimitGuard;
import com.awesome.testing.service.mfa.MfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "two-factor authentication", description = "TOTP enrollment and two-factor sign-in")
@RequiredArgsConstructor
public class UserMfaController {

    private final MfaService mfaService;
    private final AuthRateLimitGuard authRateLimitGuard;

    @PostMapping("/signin/2fa")
    @Operation(summary = "Complete sign-in with a second factor",
            description = "Consumes a short-lived password challenge and a TOTP or recovery code, then issues tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sign-in completed"),
            @ApiResponse(responseCode = "400", description = "Field validation failed"),
            @ApiResponse(responseCode = "401", description = "Challenge or second factor is invalid"),
            @ApiResponse(responseCode = "429", description = "Too many attempts")
    })
    public LoginResponseDto completeSignIn(HttpServletRequest httpRequest,
                                           @Valid @RequestBody MfaChallengeRequestDto request) {
        authRateLimitGuard.checkMfaSignIn(httpRequest, request.getChallengeToken());
        return mfaService.completeSignIn(request.getChallengeToken(), request.getCode());
    }

    @GetMapping("/2fa/status")
    @Operation(summary = "Get two-factor authentication status",
            description = "Returns enrollment state and the number of unused recovery codes.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public MfaStatusResponseDto status(@AuthenticationPrincipal CustomPrincipal principal) {
        return mfaService.status(principal.getUsername());
    }

    @PostMapping("/2fa/setup")
    @Operation(summary = "Start authenticator enrollment",
            description = "Creates a pending TOTP secret and returns its manual key and QR code.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pending setup created"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "MFA is already enabled or provider-managed"),
            @ApiResponse(responseCode = "429", description = "Too many attempts")
    })
    public MfaSetupResponseDto setup(@AuthenticationPrincipal CustomPrincipal principal) {
        authRateLimitGuard.checkMfaManagement(principal.getUsername());
        return mfaService.setup(principal.getUsername());
    }

    @PostMapping("/2fa/confirm")
    @Operation(summary = "Confirm authenticator enrollment",
            description = "Verifies the first TOTP, enables MFA, and returns one-time recovery codes.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "MFA enabled and recovery codes created"),
            @ApiResponse(responseCode = "400", description = "Field validation failed"),
            @ApiResponse(responseCode = "401", description = "Authenticator code is invalid"),
            @ApiResponse(responseCode = "409", description = "Setup is missing or MFA is already enabled"),
            @ApiResponse(responseCode = "410", description = "Pending setup expired"),
            @ApiResponse(responseCode = "429", description = "Too many attempts")
    })
    public MfaRecoveryCodesResponseDto confirm(@AuthenticationPrincipal CustomPrincipal principal,
                                               @Valid @RequestBody MfaCodeRequestDto request) {
        authRateLimitGuard.checkMfaManagement(principal.getUsername());
        return mfaService.confirm(principal.getUsername(), request.getCode());
    }

    @PostMapping("/2fa/recovery-codes")
    @Operation(summary = "Replace recovery codes",
            description = "Requires the current password and a fresh TOTP, then invalidates all previous recovery codes.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Replacement recovery codes created"),
            @ApiResponse(responseCode = "400", description = "Field validation failed"),
            @ApiResponse(responseCode = "401", description = "Password or authenticator code is invalid"),
            @ApiResponse(responseCode = "409", description = "MFA is not enabled or is provider-managed"),
            @ApiResponse(responseCode = "429", description = "Too many attempts")
    })
    public MfaRecoveryCodesResponseDto replaceRecoveryCodes(
            @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody MfaProtectedActionRequestDto request) {
        authRateLimitGuard.checkMfaManagement(principal.getUsername());
        return mfaService.regenerateRecoveryCodes(principal.getUsername(), request.getPassword(), request.getCode());
    }

    @PostMapping("/2fa/disable")
    @Operation(summary = "Disable two-factor authentication",
            description = "Requires the current password and a TOTP or recovery code, then revokes refresh tokens.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "MFA disabled"),
            @ApiResponse(responseCode = "400", description = "Field validation failed"),
            @ApiResponse(responseCode = "401", description = "Password or second factor is invalid"),
            @ApiResponse(responseCode = "409", description = "MFA is not enabled or is provider-managed"),
            @ApiResponse(responseCode = "429", description = "Too many attempts")
    })
    public void disable(@AuthenticationPrincipal CustomPrincipal principal,
                        @Valid @RequestBody MfaProtectedActionRequestDto request) {
        authRateLimitGuard.checkMfaManagement(principal.getUsername());
        mfaService.disable(principal.getUsername(), request.getPassword(), request.getCode());
    }
}
