package com.awesome.testing.controller.users;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.dto.user.SsoExchangeRequestDto;
import com.awesome.testing.service.SsoLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/sso")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
public class UserSsoController {

    private final SsoLoginService ssoLoginService;

    @PostMapping("/exchange")
    @Operation(summary = "Exchange a valid OIDC ID token for an app JWT token pair",
            description = "Verifies an external OIDC ID token, provisions or updates the matching user, and returns application tokens.")
    @ApiResponse(responseCode = "200", description = "Successfully authenticated with SSO")
    @ApiResponse(responseCode = "400", description = "Field validation failed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "401", description = "Invalid SSO token",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "SSO login is disabled",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "SSO identity conflicts with an existing local account",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorDto.class)))
    public LoginResponseDto exchange(
            @Parameter(description = "SSO exchange request") @Valid @RequestBody SsoExchangeRequestDto request) {
        return ssoLoginService.exchange(request.getIdToken());
    }

}
